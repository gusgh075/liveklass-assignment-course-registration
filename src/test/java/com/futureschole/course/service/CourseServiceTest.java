package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.UserRole;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    private static final String CREATOR_USER_ID = "creator-001";
    private static final LocalDateTime DEFAULT_START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime DEFAULT_END = LocalDateTime.of(2026, 7, 31, 18, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourseRepository courseRepository;

    @InjectMocks
    private CourseService courseService;

    private User creator;
    private CourseCreateRequest defaultRequest;

    @BeforeEach
    void setUp() {
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
}
