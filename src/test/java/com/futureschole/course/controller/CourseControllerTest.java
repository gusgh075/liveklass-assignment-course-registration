package com.futureschole.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.dto.response.CourseSummaryResponse;
import com.futureschole.course.dto.response.PageCourseSummary;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourseController.class)
class CourseControllerTest {

    private static final String CREATOR_USER_ID = "creator-001";
    private static final LocalDateTime DEFAULT_START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime DEFAULT_END = LocalDateTime.of(2026, 7, 31, 18, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourseService courseService;

    private CourseCreateRequest defaultRequest;

    @BeforeEach
    void setUp() {
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
    @DisplayName("POST /courses")
    class Create {

        @Test
        @DisplayName("ROLE_CREATOR가 유효한 요청을 보내면 201과 DRAFT 강의 상세를 반환한다")
        void create_success() throws Exception {
            // given
            LocalDateTime now = LocalDateTime.of(2026, 5, 24, 14, 15);
            CourseDetailResponse responseBody = new CourseDetailResponse(
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
                    now,
                    now
            );
            given(courseService.create(eq(CREATOR_USER_ID), any(CourseCreateRequest.class)))
                    .willReturn(responseBody);

            // when & then
            mockMvc.perform(post("/courses")
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.data.id").value(100))
                    .andExpect(jsonPath("$.data.creatorId").value(CREATOR_USER_ID))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.data.enrolledCount").value(0))
                    .andExpect(jsonPath("$.data.waitingCount").value(0))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("X-User-Role이 ROLE_USER이면 403 FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
        void create_forbiddenForRoleUser() throws Exception {
            mockMvc.perform(post("/courses")
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.error.code").value(140301))
                    .andExpect(jsonPath("$.error.message").value("FORBIDDEN"));

            verify(courseService, never()).create(any(), any());
        }

        @Test
        @DisplayName("필수 필드가 누락되거나 범위를 벗어나면 400 VALIDATION_FAILED를 반환한다")
        void create_validationFailed() throws Exception {
            Map<String, Object> invalidBody = new HashMap<>();
            invalidBody.put("title", "");                    // @NotBlank 위반
            invalidBody.put("description", "desc");
            invalidBody.put("price", -1);                    // @PositiveOrZero 위반
            invalidBody.put("capacity", 0);                  // @Min(1) 위반
            invalidBody.put("startDate", DEFAULT_START.toString());
            invalidBody.put("endDate", DEFAULT_END.toString());

            mockMvc.perform(post("/courses")
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidBody)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.error.code").value(140001))
                    .andExpect(jsonPath("$.error.message").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.error.details").isArray());

            verify(courseService, never()).create(any(), any());
        }

        @Test
        @DisplayName("서비스가 USER_NOT_FOUND를 던지면 400과 해당 에러 코드를 반환한다")
        void create_userNotFound() throws Exception {
            String unknownUserId = "ghost-user";
            given(courseService.create(eq(unknownUserId), any(CourseCreateRequest.class)))
                    .willThrow(new BusinessException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(post("/courses")
                            .header("X-User-Id", unknownUserId)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.error.code").value(140003))
                    .andExpect(jsonPath("$.error.message").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("인증 헤더가 누락되면 기존 핸들러가 USER_NOT_FOUND로 변환해 응답한다")
        void create_missingAuthHeader() throws Exception {
            mockMvc.perform(post("/courses")
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(140003))
                    .andExpect(jsonPath("$.error.message").value("USER_NOT_FOUND"));

            verify(courseService, never()).create(any(), any());
        }
    }

    @Nested
    @DisplayName("PUT /courses/{courseId}")
    class Update {

        private static final Long COURSE_ID = 100L;

        @Test
        @DisplayName("ROLE_CREATOR가 본인 DRAFT 강의를 수정하면 200과 수정된 강의 상세를 반환한다")
        void update_success() throws Exception {
            // given
            LocalDateTime now = LocalDateTime.of(2026, 5, 24, 14, 30);
            CourseDetailResponse responseBody = new CourseDetailResponse(
                    COURSE_ID,
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
                    now,
                    now
            );
            given(courseService.update(eq(CREATOR_USER_ID), eq(COURSE_ID), any(CourseCreateRequest.class)))
                    .willReturn(responseBody);

            // when & then
            mockMvc.perform(put("/courses/{courseId}", COURSE_ID)
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(100))
                    .andExpect(jsonPath("$.data.creatorId").value(CREATOR_USER_ID))
                    .andExpect(jsonPath("$.data.title").value(defaultRequest.title()))
                    .andExpect(jsonPath("$.data.status").value("DRAFT"))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("X-User-Role이 ROLE_USER이면 403 FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
        void update_forbiddenForRoleUser() throws Exception {
            mockMvc.perform(put("/courses/{courseId}", COURSE_ID)
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.error.code").value(140301))
                    .andExpect(jsonPath("$.error.message").value("FORBIDDEN"));

            verify(courseService, never()).update(any(), any(), any());
        }

        @Test
        @DisplayName("필수 필드가 누락되거나 범위를 벗어나면 400 VALIDATION_FAILED를 반환한다")
        void update_validationFailed() throws Exception {
            Map<String, Object> invalidBody = new HashMap<>();
            invalidBody.put("title", "");                    // @NotBlank 위반
            invalidBody.put("description", "desc");
            invalidBody.put("price", -1);                    // @PositiveOrZero 위반
            invalidBody.put("capacity", 0);                  // @Min(1) 위반
            invalidBody.put("startDate", DEFAULT_START.toString());
            invalidBody.put("endDate", DEFAULT_END.toString());

            mockMvc.perform(put("/courses/{courseId}", COURSE_ID)
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidBody)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.error.code").value(140001))
                    .andExpect(jsonPath("$.error.message").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.error.details").isArray());

            verify(courseService, never()).update(any(), any(), any());
        }

        @Test
        @DisplayName("서비스가 COURSE_NOT_FOUND를 던지면 404와 해당 에러 코드를 반환한다")
        void update_courseNotFound() throws Exception {
            given(courseService.update(eq(CREATOR_USER_ID), eq(COURSE_ID), any(CourseCreateRequest.class)))
                    .willThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND));

            mockMvc.perform(put("/courses/{courseId}", COURSE_ID)
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.error.code").value(240401))
                    .andExpect(jsonPath("$.error.message").value("COURSE_NOT_FOUND"));
        }

        @Test
        @DisplayName("서비스가 COURSE_NOT_EDITABLE을 던지면 409와 해당 에러 코드를 반환한다")
        void update_courseNotEditable() throws Exception {
            given(courseService.update(eq(CREATOR_USER_ID), eq(COURSE_ID), any(CourseCreateRequest.class)))
                    .willThrow(new BusinessException(ErrorCode.COURSE_NOT_EDITABLE));

            mockMvc.perform(put("/courses/{courseId}", COURSE_ID)
                            .header("X-User-Id", CREATOR_USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.error.code").value(240902))
                    .andExpect(jsonPath("$.error.message").value("COURSE_NOT_EDITABLE"));
        }

        @Test
        @DisplayName("인증 헤더가 누락되면 기존 핸들러가 USER_NOT_FOUND로 변환해 응답한다")
        void update_missingAuthHeader() throws Exception {
            mockMvc.perform(put("/courses/{courseId}", COURSE_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(140003))
                    .andExpect(jsonPath("$.error.message").value("USER_NOT_FOUND"));

            verify(courseService, never()).update(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("GET /courses/{courseId}")
    class GetDetail {

        private static final Long COURSE_ID = 100L;

        @Test
        @DisplayName("강의가 존재하면 인증 헤더 없이도 200과 신청·대기 인원이 담긴 강의 상세를 반환한다")
        void getDetail_success() throws Exception {
            // given
            LocalDateTime now = LocalDateTime.of(2026, 5, 24, 14, 30);
            CourseDetailResponse responseBody = new CourseDetailResponse(
                    COURSE_ID,
                    CREATOR_USER_ID,
                    defaultRequest.title(),
                    defaultRequest.description(),
                    defaultRequest.price(),
                    defaultRequest.capacity(),
                    7,
                    3,
                    DEFAULT_START,
                    DEFAULT_END,
                    CourseStatus.OPEN,
                    now,
                    now
            );
            given(courseService.getDetail(COURSE_ID)).willReturn(responseBody);

            // when & then
            mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(100))
                    .andExpect(jsonPath("$.data.creatorId").value(CREATOR_USER_ID))
                    .andExpect(jsonPath("$.data.status").value("OPEN"))
                    .andExpect(jsonPath("$.data.enrolledCount").value(7))
                    .andExpect(jsonPath("$.data.waitingCount").value(3))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("서비스가 COURSE_NOT_FOUND를 던지면 404와 해당 에러 코드를 반환한다")
        void getDetail_courseNotFound() throws Exception {
            given(courseService.getDetail(COURSE_ID))
                    .willThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND));

            mockMvc.perform(get("/courses/{courseId}", COURSE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.error.code").value(240401))
                    .andExpect(jsonPath("$.error.message").value("COURSE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /courses")
    class GetList {

        private PageCourseSummary samplePage() {
            CourseSummaryResponse item = new CourseSummaryResponse(
                    1L, "Spring Boot 입문", 49000, 30, 12, 0, DEFAULT_START, DEFAULT_END, CourseStatus.OPEN);
            return new PageCourseSummary(List.of(item), 0, 20, 1, 1);
        }

        @Test
        @DisplayName("status 파라미터가 없으면 기본 OPEN·CLOSED로 조회하고 200을 반환한다")
        void getList_defaultStatus() throws Exception {
            given(courseService.getList(anyList(), any(Pageable.class))).willReturn(samplePage());

            mockMvc.perform(get("/courses"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content[0].id").value(1))
                    .andExpect(jsonPath("$.data.content[0].title").value("Spring Boot 입문"))
                    .andExpect(jsonPath("$.data.content[0].enrolledCount").value(12))
                    .andExpect(jsonPath("$.data.content[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(20))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.error").doesNotExist());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CourseStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
            verify(courseService).getList(statusCaptor.capture(), any(Pageable.class));
            assertThat(statusCaptor.getValue())
                    .containsExactlyInAnyOrder(CourseStatus.OPEN, CourseStatus.CLOSED);
        }

        @Test
        @DisplayName("status 파라미터를 지정하면 그대로 서비스에 전달한다")
        void getList_explicitStatus() throws Exception {
            given(courseService.getList(anyList(), any(Pageable.class))).willReturn(samplePage());

            mockMvc.perform(get("/courses").param("status", "OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].id").value(1));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<CourseStatus>> statusCaptor = ArgumentCaptor.forClass(List.class);
            verify(courseService).getList(statusCaptor.capture(), any(Pageable.class));
            assertThat(statusCaptor.getValue()).containsExactly(CourseStatus.OPEN);
        }

        @Test
        @DisplayName("page·size 파라미터를 Pageable로 전달한다")
        void getList_paging() throws Exception {
            given(courseService.getList(anyList(), any(Pageable.class)))
                    .willReturn(new PageCourseSummary(List.of(), 2, 5, 0, 0));

            mockMvc.perform(get("/courses").param("page", "2").param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content").isArray());

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(courseService).getList(anyList(), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        }
    }
}
