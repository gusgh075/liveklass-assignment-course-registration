package com.futureschole.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.service.EnrollmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EnrollmentController.class)
class EnrollmentControllerTest {

    private static final String USER_ID = "user-001";
    private static final Long COURSE_ID = 1L;
    private static final Long ENROLLMENT_ID = 101L;
    private static final Long WAITLIST_ID = 55L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EnrollmentService enrollmentService;

    @Nested
    @DisplayName("POST /enrollments")
    class Apply {

        @Test
        @DisplayName("ROLE_USER가 정원 내 신청에 성공하면 201과 ENROLLED 결과를 반환한다")
        void apply_enrolledReturns201() throws Exception {
            LocalDateTime deadline = LocalDateTime.of(2026, 5, 24, 14, 45);
            given(enrollmentService.apply(eq(USER_ID), any(EnrollmentCreateRequest.class)))
                    .willReturn(EnrollmentCreateResponse.enrolled(ENROLLMENT_ID, COURSE_ID, deadline));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.data.resultType").value("ENROLLED"))
                    .andExpect(jsonPath("$.data.enrollmentId").value(101))
                    .andExpect(jsonPath("$.data.status").value("PENDING"))
                    .andExpect(jsonPath("$.data.waitlistId").doesNotExist())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("정원이 차서 대기열에 진입하면 201과 WAITLISTED 결과·순번을 반환한다")
        void apply_waitlistedReturns201() throws Exception {
            given(enrollmentService.apply(eq(USER_ID), any(EnrollmentCreateRequest.class)))
                    .willReturn(EnrollmentCreateResponse.waitlisted(WAITLIST_ID, COURSE_ID, 3));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value(201))
                    .andExpect(jsonPath("$.data.resultType").value("WAITLISTED"))
                    .andExpect(jsonPath("$.data.waitlistId").value(55))
                    .andExpect(jsonPath("$.data.waitlistPosition").value(3))
                    .andExpect(jsonPath("$.data.enrollmentId").doesNotExist());
        }

        @Test
        @DisplayName("X-User-Role이 ROLE_CREATOR이면 403 FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
        void apply_forbiddenForRoleCreator() throws Exception {
            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.error.code").value(140301))
                    .andExpect(jsonPath("$.error.message").value("FORBIDDEN"));

            verify(enrollmentService, never()).apply(any(), any());
        }

        @Test
        @DisplayName("courseId가 누락되면 400 VALIDATION_FAILED를 반환한다")
        void apply_validationFailed() throws Exception {
            Map<String, Object> invalidBody = new HashMap<>();    // courseId 누락 → @NotNull 위반

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalidBody)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.error.code").value(140001))
                    .andExpect(jsonPath("$.error.message").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.error.details").isArray());

            verify(enrollmentService, never()).apply(any(), any());
        }

        @Test
        @DisplayName("서비스가 COURSE_NOT_OPEN_FOR_ENROLLMENT을 던지면 409와 해당 에러 코드를 반환한다")
        void apply_courseNotOpen() throws Exception {
            given(enrollmentService.apply(eq(USER_ID), any(EnrollmentCreateRequest.class)))
                    .willThrow(new BusinessException(ErrorCode.COURSE_NOT_OPEN_FOR_ENROLLMENT));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.error.code").value(340902))
                    .andExpect(jsonPath("$.error.message").value("COURSE_NOT_OPEN_FOR_ENROLLMENT"));
        }

        @Test
        @DisplayName("서비스가 COURSE_NOT_FOUND를 던지면 404와 해당 에러 코드를 반환한다")
        void apply_courseNotFound() throws Exception {
            given(enrollmentService.apply(eq(USER_ID), any(EnrollmentCreateRequest.class)))
                    .willThrow(new BusinessException(ErrorCode.COURSE_NOT_FOUND));

            mockMvc.perform(post("/enrollments")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.error.code").value(240401))
                    .andExpect(jsonPath("$.error.message").value("COURSE_NOT_FOUND"));
        }

        @Test
        @DisplayName("인증 헤더가 누락되면 기존 핸들러가 USER_NOT_FOUND로 변환해 응답한다")
        void apply_missingAuthHeader() throws Exception {
            mockMvc.perform(post("/enrollments")
                            .header("X-User-Role", "ROLE_USER")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(COURSE_ID))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(140003))
                    .andExpect(jsonPath("$.error.message").value("USER_NOT_FOUND"));

            verify(enrollmentService, never()).apply(any(), any());
        }
    }
}
