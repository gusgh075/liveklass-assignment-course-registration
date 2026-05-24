package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.request.CourseStatusChangeRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.dto.response.CourseEnrollmentItemResponse;
import com.futureschole.course.dto.response.CourseSummaryResponse;
import com.futureschole.course.dto.response.PageCourseEnrollmentItem;
import com.futureschole.course.dto.response.PageCourseSummary;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.EnrollmentStatus;
import com.futureschole.course.entity.type.UserRole;
import com.futureschole.course.repository.CourseCountProjection;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.EnrollmentRepository;
import com.futureschole.course.repository.UserRepository;
import com.futureschole.course.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final String CREATOR_USER_ID = "creator-001";
    private static final LocalDateTime DEFAULT_START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime DEFAULT_END = LocalDateTime.of(2026, 7, 31, 18, 0);

    /** 고정 기준 시각. 종료일 경과 판정을 결정적으로 만들기 위해 {@link Clock#fixed}로 주입한다. */
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

    private CourseService courseService;

    private User creator;
    private CourseCreateRequest defaultRequest;

    @BeforeEach
    void setUp() {
        courseService = new CourseService(
                userRepository, courseRepository, enrollmentRepository, waitlistRepository, FIXED_CLOCK);

        creator = User.builder()
                .userId(CREATOR_USER_ID)
                .role(UserRole.ROLE_CREATOR)
                .build();
        ReflectionTestUtils.setField(creator, "id", 1L);

        defaultRequest = new CourseCreateRequest(
                "요리 초보자를 위한 간단한 요리 7일 코스",
                "1인가구를 위한 하기 부담스럽지 않은 요리를 위주로 소개합니다.",
                33000,
                30,
                DEFAULT_START,
                DEFAULT_END
        );
    }

    @Nested
    @DisplayName("강의 등록")
    class Create {

        @Test
        @DisplayName("유효한 요청이면 DRAFT 상태의 강의를 저장하고 상세 응답을 반환한다")
        void create_success() {
            // given
            LocalDateTime savedAt = LocalDateTime.of(2026, 5, 24, 14, 15);
            given(userRepository.findByUserId(CREATOR_USER_ID)).willReturn(Optional.of(creator));
            given(courseRepository.save(any(Course.class))).willAnswer(invocation -> {
                Course toSave = invocation.getArgument(0);
                ReflectionTestUtils.setField(toSave, "id", 100L);
                ReflectionTestUtils.setField(toSave, "createdAt", savedAt);
                ReflectionTestUtils.setField(toSave, "updatedAt", savedAt);
                return toSave;
            });

            // when
            CourseDetailResponse response = courseService.create(CREATOR_USER_ID, defaultRequest);

            // then
            CourseDetailResponse expected = new CourseDetailResponse(
                    100L,
                    CREATOR_USER_ID,
                    defaultRequest.title(),
                    defaultRequest.description(),
                    defaultRequest.price(),
                    defaultRequest.capacity(),
                    0,
                    0,
                    DEFAULT_START,
                    DEFAULT_END,
                    CourseStatus.DRAFT,
                    savedAt,
                    savedAt
            );
            assertThat(response).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("저장되는 Course 엔티티의 creator·필드 값과 DRAFT 상태가 요청 그대로 매핑된다")
        void create_persistsDraftCourseWithRequestFields() {
            // given
            given(userRepository.findByUserId(CREATOR_USER_ID)).willReturn(Optional.of(creator));
            given(courseRepository.save(any(Course.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            courseService.create(CREATOR_USER_ID, defaultRequest);

            // then
            ArgumentCaptor<Course> captor = ArgumentCaptor.forClass(Course.class);
            verify(courseRepository).save(captor.capture());
            Course expected = Course.draftOf(
                    creator,
                    defaultRequest.title(),
                    defaultRequest.description(),
                    defaultRequest.price(),
                    defaultRequest.capacity(),
                    defaultRequest.startDate(),
                    defaultRequest.endDate()
            );
            assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("외부 식별자에 해당하는 사용자가 없으면 USER_NOT_FOUND를 던지고 저장은 호출하지 않는다")
        void create_userNotFound() {
            // given
            String unknownUserId = "ghost-user";
            given(userRepository.findByUserId(unknownUserId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> courseService.create(unknownUserId, defaultRequest))
                    .as("외부 식별자에 해당하는 사용자가 없으면 USER_NOT_FOUND 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);

            verify(courseRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("강의 수정")
    class Update {

        private static final Long COURSE_ID = 100L;
        private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 22, 14, 15);
        private static final LocalDateTime UPDATED_START = LocalDateTime.of(2026, 9, 1, 9, 0);
        private static final LocalDateTime UPDATED_END = LocalDateTime.of(2026, 10, 31, 18, 0);

        private Course existingDraft;
        private CourseCreateRequest updateRequest;

        @BeforeEach
        void setUpUpdate() {
            existingDraft = Course.draftOf(
                    creator,
                    "수정 전 제목",
                    "수정 전 설명입니다.",
                    10000,
                    10,
                    DEFAULT_START,
                    DEFAULT_END
            );
            ReflectionTestUtils.setField(existingDraft, "id", COURSE_ID);
            ReflectionTestUtils.setField(existingDraft, "createdAt", CREATED_AT);
            ReflectionTestUtils.setField(existingDraft, "updatedAt", CREATED_AT);

            updateRequest = new CourseCreateRequest(
                    "수정 후 제목",
                    "수정 후 설명입니다.",
                    50000,
                    40,
                    UPDATED_START,
                    UPDATED_END
            );
        }

        @Test
        @DisplayName("본인의 DRAFT 강의면 모든 필드를 교체하고 상세 응답을 반환한다")
        void update_success() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(existingDraft));

            // when
            CourseDetailResponse response = courseService.update(CREATOR_USER_ID, COURSE_ID, updateRequest);

            // then
            CourseDetailResponse expected = new CourseDetailResponse(
                    COURSE_ID,
                    CREATOR_USER_ID,
                    updateRequest.title(),
                    updateRequest.description(),
                    updateRequest.price(),
                    updateRequest.capacity(),
                    0,
                    0,
                    UPDATED_START,
                    UPDATED_END,
                    CourseStatus.DRAFT,
                    CREATED_AT,
                    CREATED_AT
            );
            assertThat(response).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("더티 체킹으로 영속화하므로 save를 명시 호출하지 않는다")
        void update_persistsByDirtyCheckingWithoutSave() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(existingDraft));

            // when
            courseService.update(CREATOR_USER_ID, COURSE_ID, updateRequest);

            // then
            verify(courseRepository, never()).save(any());
        }

        @Test
        @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND를 던진다")
        void update_courseNotFound() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> courseService.update(CREATOR_USER_ID, COURSE_ID, updateRequest))
                    .as("존재하지 않는 강의를 수정하면 COURSE_NOT_FOUND 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);
        }

        @Test
        @DisplayName("본인이 작성한 강의가 아니면 COURSE_NOT_OWNED를 던진다")
        void update_courseNotOwned() {
            // given
            User otherCreator = User.builder()
                    .userId("creator-999")
                    .role(UserRole.ROLE_CREATOR)
                    .build();
            ReflectionTestUtils.setField(otherCreator, "id", 2L);
            ReflectionTestUtils.setField(existingDraft, "creator", otherCreator);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(existingDraft));

            // when & then
            assertThatThrownBy(() -> courseService.update(CREATOR_USER_ID, COURSE_ID, updateRequest))
                    .as("타인의 강의를 수정하면 COURSE_NOT_OWNED 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_OWNED);
        }

        @Test
        @DisplayName("DRAFT가 아닌 강의를 수정하면 COURSE_NOT_EDITABLE을 던진다")
        void update_courseNotEditable() {
            // given
            ReflectionTestUtils.setField(existingDraft, "status", CourseStatus.OPEN);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(existingDraft));

            // when & then
            assertThatThrownBy(() -> courseService.update(CREATOR_USER_ID, COURSE_ID, updateRequest))
                    .as("DRAFT가 아닌 강의를 수정하면 COURSE_NOT_EDITABLE 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_EDITABLE);
        }
    }

    @Nested
    @DisplayName("강의 상세 조회")
    class GetDetail {

        private static final Long COURSE_ID = 100L;
        private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 22, 14, 15);

        private Course existingCourse;

        @BeforeEach
        void setUpGetDetail() {
            existingCourse = Course.draftOf(
                    creator,
                    "요리 초보자를 위한 간단한 요리 7일 코스",
                    "1인가구를 위한 하기 부담스럽지 않은 요리를 위주로 소개합니다.",
                    33000,
                    30,
                    DEFAULT_START,
                    DEFAULT_END
            );
            ReflectionTestUtils.setField(existingCourse, "id", COURSE_ID);
            ReflectionTestUtils.setField(existingCourse, "status", CourseStatus.OPEN);
            ReflectionTestUtils.setField(existingCourse, "createdAt", CREATED_AT);
            ReflectionTestUtils.setField(existingCourse, "updatedAt", CREATED_AT);
        }

        @Test
        @DisplayName("강의가 존재하면 신청 인원·대기 인원 카운트를 합쳐 상세 응답을 반환한다")
        void getDetail_success() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(existingCourse));
            given(enrollmentRepository.countByCourseAndStatusIn(
                    existingCourse, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)))
                    .willReturn(7);
            given(waitlistRepository.countByCourse(existingCourse)).willReturn(3);

            // when
            CourseDetailResponse response = courseService.getDetail(COURSE_ID);

            // then
            CourseDetailResponse expected = new CourseDetailResponse(
                    COURSE_ID,
                    CREATOR_USER_ID,
                    existingCourse.getTitle(),
                    existingCourse.getDescription(),
                    existingCourse.getPrice(),
                    existingCourse.getCapacity(),
                    7,
                    3,
                    DEFAULT_START,
                    DEFAULT_END,
                    CourseStatus.OPEN,
                    CREATED_AT,
                    CREATED_AT
            );
            assertThat(response).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND를 던지고 카운트는 조회하지 않는다")
        void getDetail_courseNotFound() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> courseService.getDetail(COURSE_ID))
                    .as("존재하지 않는 강의를 조회하면 COURSE_NOT_FOUND 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);

            verify(enrollmentRepository, never()).countByCourseAndStatusIn(any(), any());
            verify(waitlistRepository, never()).countByCourse(any());
        }
    }

    @Nested
    @DisplayName("강의 목록 조회")
    class GetList {

        private static final List<EnrollmentStatus> ACTIVE =
                List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

        @Test
        @DisplayName("상태로 필터한 페이지를 조회하고 강의별 신청·대기 인원을 매핑해 반환한다")
        void getList_mapsCountsPerCourse() {
            // given
            List<CourseStatus> statuses = List.of(CourseStatus.OPEN, CourseStatus.CLOSED);
            Pageable pageable = PageRequest.of(0, 20);
            Course open = courseWithId(10L, CourseStatus.OPEN);
            Course closed = courseWithId(20L, CourseStatus.CLOSED);
            given(courseRepository.findByStatusIn(statuses, pageable))
                    .willReturn(new PageImpl<>(List.of(open, closed), pageable, 2));
            given(enrollmentRepository.countActiveByCourseIds(List.of(10L, 20L), ACTIVE))
                    .willReturn(List.of(projection(10L, 3L), projection(20L, 5L)));
            given(waitlistRepository.countByCourseIds(List.of(10L, 20L)))
                    .willReturn(List.of(projection(10L, 2L)));

            // when
            PageCourseSummary result = courseService.getList(statuses, pageable);

            // then
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.totalPages()).isEqualTo(1);
            assertThat(result.content())
                    .extracting(CourseSummaryResponse::id,
                            CourseSummaryResponse::enrolledCount,
                            CourseSummaryResponse::waitingCount,
                            CourseSummaryResponse::status)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(10L, 3, 2, CourseStatus.OPEN),
                            org.assertj.core.groups.Tuple.tuple(20L, 5, 0, CourseStatus.CLOSED));
        }

        @Test
        @DisplayName("집계 결과에 없는 강의의 신청·대기 인원은 0으로 채운다")
        void getList_defaultsMissingCountsToZero() {
            // given
            List<CourseStatus> statuses = List.of(CourseStatus.OPEN);
            Pageable pageable = PageRequest.of(0, 20);
            Course open = courseWithId(10L, CourseStatus.OPEN);
            given(courseRepository.findByStatusIn(statuses, pageable))
                    .willReturn(new PageImpl<>(List.of(open), pageable, 1));
            given(enrollmentRepository.countActiveByCourseIds(List.of(10L), ACTIVE))
                    .willReturn(List.of());
            given(waitlistRepository.countByCourseIds(List.of(10L)))
                    .willReturn(List.of());

            // when
            PageCourseSummary result = courseService.getList(statuses, pageable);

            // then
            assertThat(result.content()).hasSize(1);
            assertThat(result.content().get(0).enrolledCount()).isZero();
            assertThat(result.content().get(0).waitingCount()).isZero();
        }

        @Test
        @DisplayName("빈 페이지면 배치 카운트 쿼리를 던지지 않고 빈 콘텐츠를 반환한다")
        void getList_emptyPageSkipsBatchCount() {
            // given
            List<CourseStatus> statuses = List.of(CourseStatus.OPEN, CourseStatus.CLOSED);
            Pageable pageable = PageRequest.of(0, 20);
            given(courseRepository.findByStatusIn(statuses, pageable))
                    .willReturn(new PageImpl<>(List.of(), pageable, 0));

            // when
            PageCourseSummary result = courseService.getList(statuses, pageable);

            // then
            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isZero();
            verify(enrollmentRepository, never()).countActiveByCourseIds(anyList(), eq(ACTIVE));
            verify(waitlistRepository, never()).countByCourseIds(anyList());
        }

        private Course courseWithId(Long id, CourseStatus status) {
            Course course = Course.draftOf(creator, "강의 " + id, "설명", 10000, 30, DEFAULT_START, DEFAULT_END);
            ReflectionTestUtils.setField(course, "id", id);
            ReflectionTestUtils.setField(course, "status", status);
            return course;
        }

        private CourseCountProjection projection(Long courseId, long count) {
            return new CourseCountProjection() {
                @Override
                public Long getCourseId() {
                    return courseId;
                }

                @Override
                public long getCount() {
                    return count;
                }
            };
        }
    }

    @Nested
    @DisplayName("강의 상태 변경")
    class ChangeStatus {

        private static final Long COURSE_ID = 100L;
        private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 5, 22, 14, 15);

        private Course course(CourseStatus status, LocalDateTime endDate) {
            Course course = Course.draftOf(
                    creator,
                    "요리 초보자를 위한 간단한 요리 7일 코스",
                    "1인가구를 위한 하기 부담스럽지 않은 요리를 위주로 소개합니다.",
                    33000,
                    30,
                    DEFAULT_START,
                    endDate
            );
            ReflectionTestUtils.setField(course, "id", COURSE_ID);
            ReflectionTestUtils.setField(course, "status", status);
            ReflectionTestUtils.setField(course, "createdAt", CREATED_AT);
            ReflectionTestUtils.setField(course, "updatedAt", CREATED_AT);
            return course;
        }

        @Test
        @DisplayName("본인의 DRAFT 강의를 OPEN으로 전이하고 신청·대기 인원을 합쳐 상세 응답을 반환한다")
        void changeStatus_draftToOpen() {
            // given: 종료일은 현재(NOW) 이후라 오픈 가능
            Course draft = course(CourseStatus.DRAFT, DEFAULT_END);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(draft));
            given(enrollmentRepository.countByCourseAndStatusIn(
                    draft, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)))
                    .willReturn(0);
            given(waitlistRepository.countByCourse(draft)).willReturn(0);

            // when
            CourseDetailResponse response =
                    courseService.changeStatus(CREATOR_USER_ID, COURSE_ID, CourseStatus.OPEN);

            // then
            assertThat(draft.getStatus()).isEqualTo(CourseStatus.OPEN);
            assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
            assertThat(response.id()).isEqualTo(COURSE_ID);
            assertThat(response.enrolledCount()).isZero();
            assertThat(response.waitingCount()).isZero();
            verify(courseRepository, never()).save(any());
        }

        @Test
        @DisplayName("본인의 OPEN 강의를 CLOSED로 전이하고 신청·대기 인원을 합쳐 상세 응답을 반환한다")
        void changeStatus_openToClosed() {
            // given
            Course open = course(CourseStatus.OPEN, DEFAULT_END);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(open));
            given(enrollmentRepository.countByCourseAndStatusIn(
                    open, List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)))
                    .willReturn(5);
            given(waitlistRepository.countByCourse(open)).willReturn(2);

            // when
            CourseDetailResponse response =
                    courseService.changeStatus(CREATOR_USER_ID, COURSE_ID, CourseStatus.CLOSED);

            // then
            assertThat(open.getStatus()).isEqualTo(CourseStatus.CLOSED);
            assertThat(response.status()).isEqualTo(CourseStatus.CLOSED);
            assertThat(response.enrolledCount()).isEqualTo(5);
            assertThat(response.waitingCount()).isEqualTo(2);
            verify(courseRepository, never()).save(any());
        }

        @Test
        @DisplayName("허용되지 않은 전이(OPEN → OPEN 등)이면 COURSE_ILLEGAL_TRANSITION을 던진다")
        void changeStatus_illegalTransition() {
            // given: OPEN 강의를 다시 OPEN으로 요청 → 허용되지 않음
            Course open = course(CourseStatus.OPEN, DEFAULT_END);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(open));

            // when & then
            assertThatThrownBy(() -> courseService.changeStatus(CREATOR_USER_ID, COURSE_ID, CourseStatus.OPEN))
                    .as("허용되지 않은 전이는 COURSE_ILLEGAL_TRANSITION 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_ILLEGAL_TRANSITION);

            assertThat(open.getStatus()).isEqualTo(CourseStatus.OPEN);
        }

        @Test
        @DisplayName("종료일이 경과한 DRAFT 강의를 OPEN하려 하면 COURSE_ENDED를 던진다")
        void changeStatus_endedWhenOpening() {
            // given: 종료일을 현재(NOW)보다 과거로 설정
            Course draft = course(CourseStatus.DRAFT, NOW.minusDays(1));
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(draft));

            // when & then
            assertThatThrownBy(() -> courseService.changeStatus(CREATOR_USER_ID, COURSE_ID, CourseStatus.OPEN))
                    .as("종료일이 경과한 강의를 오픈하면 COURSE_ENDED 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_ENDED);

            assertThat(draft.getStatus()).isEqualTo(CourseStatus.DRAFT);
        }

        @Test
        @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND를 던진다")
        void changeStatus_courseNotFound() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> courseService.changeStatus(CREATOR_USER_ID, COURSE_ID, CourseStatus.OPEN))
                    .as("존재하지 않는 강의는 COURSE_NOT_FOUND 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);
        }

        @Test
        @DisplayName("본인이 작성한 강의가 아니면 COURSE_NOT_OWNED를 던진다")
        void changeStatus_courseNotOwned() {
            // given
            User otherCreator = User.builder()
                    .userId("creator-999")
                    .role(UserRole.ROLE_CREATOR)
                    .build();
            ReflectionTestUtils.setField(otherCreator, "id", 2L);
            Course draft = course(CourseStatus.DRAFT, DEFAULT_END);
            ReflectionTestUtils.setField(draft, "creator", otherCreator);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(draft));

            // when & then
            assertThatThrownBy(() -> courseService.changeStatus(CREATOR_USER_ID, COURSE_ID, CourseStatus.OPEN))
                    .as("타인의 강의 상태 변경은 COURSE_NOT_OWNED 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_OWNED);

            assertThat(draft.getStatus()).isEqualTo(CourseStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("강의별 수강생 목록 조회")
    class GetCourseEnrollments {

        private static final Long COURSE_ID = 100L;
        private static final List<EnrollmentStatus> ACTIVE =
                List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

        private Course existingCourse;

        @BeforeEach
        void setUpGetCourseEnrollments() {
            existingCourse = Course.draftOf(
                    creator, "요리 7일 코스", "설명", 33000, 30, DEFAULT_START, DEFAULT_END);
            ReflectionTestUtils.setField(existingCourse, "id", COURSE_ID);
            ReflectionTestUtils.setField(existingCourse, "status", CourseStatus.OPEN);
        }

        private Enrollment enrollment(Long id, String userId, EnrollmentStatus status,
                                      LocalDateTime confirmedAt, LocalDateTime createdAt) {
            User user = User.builder().userId(userId).role(UserRole.ROLE_USER).build();
            Enrollment enrollment = Enrollment.pending(user, existingCourse);
            ReflectionTestUtils.setField(enrollment, "id", id);
            ReflectionTestUtils.setField(enrollment, "status", status);
            ReflectionTestUtils.setField(enrollment, "confirmedAt", confirmedAt);
            ReflectionTestUtils.setField(enrollment, "createdAt", createdAt);
            return enrollment;
        }

        @Test
        @DisplayName("본인 강의면 활성 신청 페이지를 항목으로 매핑해 반환한다")
        void getCourseEnrollments_success() {
            // given
            Pageable pageable = PageRequest.of(0, 20);
            LocalDateTime createdAt = LocalDateTime.of(2026, 5, 22, 14, 15);
            LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 22, 14, 30);
            Enrollment pending = enrollment(101L, "user-001", EnrollmentStatus.PENDING, null, createdAt);
            Enrollment confirmed = enrollment(102L, "user-002", EnrollmentStatus.CONFIRMED, confirmedAt, createdAt);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(existingCourse));
            given(enrollmentRepository.findByCourseAndStatusIn(existingCourse, ACTIVE, pageable))
                    .willReturn(new PageImpl<>(List.of(pending, confirmed), pageable, 2));

            // when
            PageCourseEnrollmentItem result =
                    courseService.getCourseEnrollments(COURSE_ID, CREATOR_USER_ID, pageable);

            // then
            assertThat(result.page()).isZero();
            assertThat(result.size()).isEqualTo(20);
            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.totalPages()).isEqualTo(1);
            assertThat(result.content())
                    .extracting(CourseEnrollmentItemResponse::enrollmentId,
                            CourseEnrollmentItemResponse::userId,
                            CourseEnrollmentItemResponse::status,
                            CourseEnrollmentItemResponse::confirmedAt)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple(101L, "user-001", EnrollmentStatus.PENDING, null),
                            org.assertj.core.groups.Tuple.tuple(102L, "user-002", EnrollmentStatus.CONFIRMED, confirmedAt));
        }

        @Test
        @DisplayName("강의가 존재하지 않으면 COURSE_NOT_FOUND를 던지고 신청은 조회하지 않는다")
        void getCourseEnrollments_courseNotFound() {
            // given
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() ->
                    courseService.getCourseEnrollments(COURSE_ID, CREATOR_USER_ID, PageRequest.of(0, 20)))
                    .as("존재하지 않는 강의의 수강생 목록 조회는 COURSE_NOT_FOUND 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_FOUND);

            verify(enrollmentRepository, never()).findByCourseAndStatusIn(any(), any(), any());
        }

        @Test
        @DisplayName("본인이 작성한 강의가 아니면 COURSE_NOT_OWNED를 던지고 신청은 조회하지 않는다")
        void getCourseEnrollments_courseNotOwned() {
            // given
            User otherCreator = User.builder().userId("creator-999").role(UserRole.ROLE_CREATOR).build();
            ReflectionTestUtils.setField(otherCreator, "id", 2L);
            ReflectionTestUtils.setField(existingCourse, "creator", otherCreator);
            given(courseRepository.findById(COURSE_ID)).willReturn(Optional.of(existingCourse));

            // when & then
            assertThatThrownBy(() ->
                    courseService.getCourseEnrollments(COURSE_ID, CREATOR_USER_ID, PageRequest.of(0, 20)))
                    .as("타인 강의의 수강생 목록 조회는 COURSE_NOT_OWNED 코드의 BusinessException이 발생해야 한다")
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COURSE_NOT_OWNED);

            verify(enrollmentRepository, never()).findByCourseAndStatusIn(any(), any(), any());
        }
    }
}
