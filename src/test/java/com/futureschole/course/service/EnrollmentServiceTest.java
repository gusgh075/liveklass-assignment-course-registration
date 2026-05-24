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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
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

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private WaitlistRepository waitlistRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private User user;
    private Course course;
    private EnrollmentCreateRequest request;

    @BeforeEach
    void setUp() {
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
}
