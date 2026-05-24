package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.EnrollmentResultType;
import com.futureschole.course.entity.type.UserRole;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.EnrollmentRepository;
import com.futureschole.course.repository.UserRepository;
import com.futureschole.course.repository.WaitlistRepository;
import com.futureschole.course.dto.response.EnrollmentResponse;
import com.futureschole.course.dto.response.PageMyEnrollmentItem;
import com.futureschole.course.entity.type.EnrollmentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    private static final String USER_ID = "user-001";
    private static final Long COURSE_ID = 1L;
    private static final int CAPACITY = 2;
    private static final Long ENROLLMENT_ID = 101L;
    private static final Long WAITLIST_ID = 55L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 24, 14, 15);
    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 18, 0);

    /** 고정 기준 시각. 결제 기한 판정을 결정적으로 만들기 위해 {@link Clock#fixed}로 주입한다. */
    private static final ZoneId ZONE = ZoneOffset.UTC;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 24, 14, 30);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZONE);

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private WaitlistRepository waitlistRepository;

    private EnrollmentService enrollmentService;

    private User user;
    private Course course;
    private EnrollmentCreateRequest request;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(
                userRepository, courseRepository, enrollmentRepository, waitlistRepository, FIXED_CLOCK);

        user = User.builder().userId(USER_ID).role(UserRole.ROLE_USER).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        User creator = User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build();
        course = Course.draftOf(creator, "제목", "설명", 10000, CAPACITY, START, END);
        ReflectionTestUtils.setField(course, "id", COURSE_ID);
        ReflectionTestUtils.setField(course, "status", CourseStatus.OPEN);

        request = new EnrollmentCreateRequest(COURSE_ID);
    }

    @Test
    @DisplayName("정원에 자리가 있으면 PENDING 신청을 만들고 ENROLLED 응답을 반환한다")
    void apply_enrolledWhenCapacityAvailable() {
        // given
        given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
        given(courseRepository.findByIdForUpdate(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByUserAndCourseAndStatusIn(eq(user), eq(course), anyCollection())).willReturn(false);
        given(waitlistRepository.existsByUserAndCourse(user, course)).willReturn(false);
        given(enrollmentRepository.countByCourseAndStatusIn(eq(course), anyCollection())).willReturn(0);
        given(enrollmentRepository.save(any(Enrollment.class))).willAnswer(invocation -> {
            Enrollment toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", ENROLLMENT_ID);
            ReflectionTestUtils.setField(toSave, "createdAt", CREATED_AT);
            ReflectionTestUtils.setField(toSave, "updatedAt", CREATED_AT);
            return toSave;
        });

        // when
        EnrollmentCreateResponse response = enrollmentService.apply(USER_ID, request);

        // then
        EnrollmentCreateResponse expected =
                EnrollmentCreateResponse.enrolled(ENROLLMENT_ID, COURSE_ID, CREATED_AT.plusMinutes(30));
        assertThat(response).usingRecursiveComparison().isEqualTo(expected);
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("정원이 차 있으면 대기열에 등록하고 WAITLISTED 응답과 순번을 반환한다")
    void apply_waitlistedWhenFull() {
        // given
        given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
        given(courseRepository.findByIdForUpdate(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByUserAndCourseAndStatusIn(eq(user), eq(course), anyCollection())).willReturn(false);
        given(waitlistRepository.existsByUserAndCourse(user, course)).willReturn(false);
        given(enrollmentRepository.countByCourseAndStatusIn(eq(course), anyCollection())).willReturn(CAPACITY);
        given(waitlistRepository.save(any(Waitlist.class))).willAnswer(invocation -> {
            Waitlist toSave = invocation.getArgument(0);
            ReflectionTestUtils.setField(toSave, "id", WAITLIST_ID);
            return toSave;
        });
        given(waitlistRepository.countByCourse(course)).willReturn(3);

        // when
        EnrollmentCreateResponse response = enrollmentService.apply(USER_ID, request);

        // then
        EnrollmentCreateResponse expected = EnrollmentCreateResponse.waitlisted(WAITLIST_ID, COURSE_ID, 3);
        assertThat(response).usingRecursiveComparison().isEqualTo(expected);
        assertThat(response.resultType()).isEqualTo(EnrollmentResultType.WAITLISTED);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("동일 강의에 활성 신청이 이미 있으면 DUPLICATE_ACTIVE_ENROLLMENT을 던진다")
    void apply_duplicateActiveEnrollment() {
        // given
        given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
        given(courseRepository.findByIdForUpdate(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByUserAndCourseAndStatusIn(eq(user), eq(course), anyCollection())).willReturn(true);

        // when & then
        assertThatThrownBy(() -> enrollmentService.apply(USER_ID, request))
                .as("활성 신청이 이미 있으면 DUPLICATE_ACTIVE_ENROLLMENT 코드의 BusinessException이 발생해야 한다")
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_ACTIVE_ENROLLMENT);

        verify(enrollmentRepository, never()).save(any());
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 대기열에 들어가 있으면 DUPLICATE_ACTIVE_ENROLLMENT을 던진다")
    void apply_duplicateInWaitlist() {
        // given
        given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
        given(courseRepository.findByIdForUpdate(COURSE_ID)).willReturn(Optional.of(course));
        given(enrollmentRepository.existsByUserAndCourseAndStatusIn(eq(user), eq(course), anyCollection())).willReturn(false);
        given(waitlistRepository.existsByUserAndCourse(user, course)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> enrollmentService.apply(USER_ID, request))
                .as("대기열에 이미 있으면 DUPLICATE_ACTIVE_ENROLLMENT 코드의 BusinessException이 발생해야 한다")
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_ACTIVE_ENROLLMENT);

        verify(enrollmentRepository, never()).save(any());
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("강의 상태가 OPEN이 아니면 COURSE_NOT_OPEN_FOR_ENROLLMENT을 던진다")
    void apply_courseNotOpen() {
        // given
        ReflectionTestUtils.setField(course, "status", CourseStatus.CLOSED);
        given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
        given(courseRepository.findByIdForUpdate(COURSE_ID)).willReturn(Optional.of(course));

        // when & then
        assertThatThrownBy(() -> enrollmentService.apply(USER_ID, request))
                .as("OPEN이 아닌 강의 신청은 COURSE_NOT_OPEN_FOR_ENROLLMENT 코드의 BusinessException이 발생해야 한다")
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_OPEN_FOR_ENROLLMENT);

        verify(enrollmentRepository, never()).save(any());
        verify(waitlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND를 던진다")
    void apply_courseNotFound() {
        // given
        given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
        given(courseRepository.findByIdForUpdate(COURSE_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> enrollmentService.apply(USER_ID, request))
                .as("존재하지 않는 강의 신청은 COURSE_NOT_FOUND 코드의 BusinessException이 발생해야 한다")
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);
    }

    @Test
    @DisplayName("사용자가 존재하지 않으면 USER_NOT_FOUND를 던진다")
    void apply_userNotFound() {
        // given
        given(userRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> enrollmentService.apply(USER_ID, request))
                .as("존재하지 않는 사용자 신청은 USER_NOT_FOUND 코드의 BusinessException이 발생해야 한다")
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Nested
    @DisplayName("confirm: 결제 확정")
    class Confirm {

        @Test
        @DisplayName("본인의 PENDING 신청을 결제 기한 안에 확정하면 CONFIRMED로 전이하고 응답을 반환한다")
        void confirm_success() {
            // given: 14:15 생성 → 기한 14:45, 현재 14:30 (기한 내)
            Enrollment enrollment = pendingEnrollment(user, CREATED_AT);
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            // when
            EnrollmentResponse response = enrollmentService.confirm(USER_ID, ENROLLMENT_ID);

            // then
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(enrollment.getConfirmedAt()).isEqualTo(NOW);
            assertThat(response.enrollmentId()).isEqualTo(ENROLLMENT_ID);
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.courseId()).isEqualTo(COURSE_ID);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(response.confirmedAt()).isEqualTo(NOW);
            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("신청이 존재하지 않으면 ENROLLMENT_NOT_FOUND를 던진다")
        void confirm_notFound() {
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.confirm(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("본인의 신청이 아니면 ENROLLMENT_NOT_OWNED를 던진다")
        void confirm_notOwned() {
            User other = User.builder().userId("user-999").role(UserRole.ROLE_USER).build();
            ReflectionTestUtils.setField(other, "id", 2L);
            Enrollment enrollment = pendingEnrollment(other, CREATED_AT);
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.confirm(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENROLLMENT_NOT_OWNED);
        }

        @Test
        @DisplayName("이미 CONFIRMED 상태이면 INVALID_STATUS_FOR_CONFIRM을 던진다")
        void confirm_invalidStatus() {
            Enrollment enrollment = pendingEnrollment(user, CREATED_AT);
            enrollment.confirm(CREATED_AT.plusMinutes(5));    // 이미 확정된 신청
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.confirm(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_STATUS_FOR_CONFIRM);
        }

        @Test
        @DisplayName("결제 기한 30분이 지났으면 PAYMENT_DEADLINE_EXPIRED를 던진다")
        void confirm_deadlineExpired() {
            // given: 13:50 생성 → 기한 14:20, 현재 14:30 (기한 초과)
            Enrollment enrollment = pendingEnrollment(user, LocalDateTime.of(2026, 5, 24, 13, 50));
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.confirm(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PAYMENT_DEADLINE_EXPIRED);
        }

        private Enrollment pendingEnrollment(User owner, LocalDateTime createdAt) {
            Enrollment enrollment = Enrollment.pending(owner, course);
            ReflectionTestUtils.setField(enrollment, "id", ENROLLMENT_ID);
            ReflectionTestUtils.setField(enrollment, "createdAt", createdAt);
            ReflectionTestUtils.setField(enrollment, "updatedAt", createdAt);
            return enrollment;
        }
    }

    @Nested
    @DisplayName("cancel: 수강 취소")
    class Cancel {

        @Test
        @DisplayName("본인의 CONFIRMED 신청을 환불창 안에 취소하면 CANCELLED로 전이하고 응답을 반환한다")
        void cancel_success() {
            // given: 확정 5/24 14:00 → 환불창 만료 5/31 14:00, 현재 5/24 14:30 (환불창 내)
            Enrollment enrollment = confirmedEnrollment(user, LocalDateTime.of(2026, 5, 24, 14, 0));
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            // when
            EnrollmentResponse response = enrollmentService.cancel(USER_ID, ENROLLMENT_ID);

            // then
            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollment.getCancelledAt()).isEqualTo(NOW);
            assertThat(response.enrollmentId()).isEqualTo(ENROLLMENT_ID);
            assertThat(response.userId()).isEqualTo(USER_ID);
            assertThat(response.courseId()).isEqualTo(COURSE_ID);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(response.cancelledAt()).isEqualTo(NOW);
            verify(enrollmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("신청이 존재하지 않으면 ENROLLMENT_NOT_FOUND를 던진다")
        void cancel_notFound() {
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.cancel(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENROLLMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("본인의 신청이 아니면 ENROLLMENT_NOT_OWNED를 던진다")
        void cancel_notOwned() {
            User other = User.builder().userId("user-999").role(UserRole.ROLE_USER).build();
            ReflectionTestUtils.setField(other, "id", 2L);
            Enrollment enrollment = confirmedEnrollment(other, LocalDateTime.of(2026, 5, 24, 14, 0));
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.cancel(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ENROLLMENT_NOT_OWNED);
        }

        @Test
        @DisplayName("상태가 CONFIRMED가 아니면(예: PENDING) INVALID_STATUS_FOR_CANCEL을 던진다")
        void cancel_invalidStatus() {
            Enrollment enrollment = Enrollment.pending(user, course);    // PENDING 상태
            ReflectionTestUtils.setField(enrollment, "id", ENROLLMENT_ID);
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.cancel(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_STATUS_FOR_CANCEL);
        }

        @Test
        @DisplayName("결제 확정 후 7일이 지났으면 REFUND_WINDOW_EXPIRED를 던진다")
        void cancel_refundWindowExpired() {
            // given: 확정 5/17 14:00 → 환불창 만료 5/24 14:00, 현재 5/24 14:30 (환불창 초과)
            Enrollment enrollment = confirmedEnrollment(user, LocalDateTime.of(2026, 5, 17, 14, 0));
            given(enrollmentRepository.findById(ENROLLMENT_ID)).willReturn(Optional.of(enrollment));

            assertThatThrownBy(() -> enrollmentService.cancel(USER_ID, ENROLLMENT_ID))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REFUND_WINDOW_EXPIRED);
        }

        private Enrollment confirmedEnrollment(User owner, LocalDateTime confirmedAt) {
            Enrollment enrollment = Enrollment.pending(owner, course);
            ReflectionTestUtils.setField(enrollment, "id", ENROLLMENT_ID);
            ReflectionTestUtils.setField(enrollment, "createdAt", confirmedAt.minusMinutes(5));
            ReflectionTestUtils.setField(enrollment, "updatedAt", confirmedAt);
            enrollment.confirm(confirmedAt);
            return enrollment;
        }
    }

    @Nested
    @DisplayName("getMyEnrollments: 내 신청 목록 조회")
    class GetMyEnrollments {

        @Test
        @DisplayName("본인 신청을 페이지로 조회해 항목을 강의 정보와 함께 매핑한 페이지 응답을 반환한다")
        void getMyEnrollments_success() {
            // given
            ReflectionTestUtils.setField(course, "title", "제목");
            Enrollment enrollment = Enrollment.pending(user, course);
            ReflectionTestUtils.setField(enrollment, "id", ENROLLMENT_ID);
            ReflectionTestUtils.setField(enrollment, "createdAt", CREATED_AT);
            ReflectionTestUtils.setField(enrollment, "updatedAt", CREATED_AT);

            Pageable pageable = PageRequest.of(0, 20);
            Page<Enrollment> page = new PageImpl<>(List.of(enrollment), pageable, 1);
            given(userRepository.findByUserId(USER_ID)).willReturn(Optional.of(user));
            given(enrollmentRepository.findByUser(user, pageable)).willReturn(page);

            // when
            PageMyEnrollmentItem response = enrollmentService.getMyEnrollments(USER_ID, pageable);

            // then
            assertThat(response.page()).isEqualTo(0);
            assertThat(response.size()).isEqualTo(20);
            assertThat(response.totalElements()).isEqualTo(1);
            assertThat(response.totalPages()).isEqualTo(1);
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).enrollmentId()).isEqualTo(ENROLLMENT_ID);
            assertThat(response.content().get(0).courseId()).isEqualTo(COURSE_ID);
            assertThat(response.content().get(0).courseTitle()).isEqualTo("제목");
            assertThat(response.content().get(0).price()).isEqualTo(10000);
            assertThat(response.content().get(0).capacity()).isEqualTo(CAPACITY);
            assertThat(response.content().get(0).status()).isEqualTo(EnrollmentStatus.PENDING);
        }

        @Test
        @DisplayName("사용자가 존재하지 않으면 USER_NOT_FOUND를 던지고 신청을 조회하지 않는다")
        void getMyEnrollments_userNotFound() {
            Pageable pageable = PageRequest.of(0, 20);
            given(userRepository.findByUserId(USER_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.getMyEnrollments(USER_ID, pageable))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

            verify(enrollmentRepository, never()).findByUser(any(), any());
        }
    }
}
