package com.futureschole.course.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.dto.response.EnrollmentResponse;
import com.futureschole.course.dto.response.MyEnrollmentItemResponse;
import com.futureschole.course.dto.response.PageMyEnrollmentItem;
import com.futureschole.course.entity.type.EnrollmentStatus;
import com.futureschole.course.service.EnrollmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Nested
    @DisplayName("POST /enrollments/{enrollmentId}/confirm")
    class Confirm {

        private EnrollmentResponse confirmedResponse() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 5, 24, 14, 15);
            LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 24, 14, 30);
            return new EnrollmentResponse(
                    ENROLLMENT_ID, USER_ID, COURSE_ID, EnrollmentStatus.CONFIRMED,
                    confirmedAt, null, createdAt, confirmedAt);
        }

        @Test
        @DisplayName("ROLE_USER가 결제 확정에 성공하면 200과 CONFIRMED 신청 정보를 반환한다")
        void confirm_returns200() throws Exception {
            given(enrollmentService.confirm(eq(USER_ID), eq(ENROLLMENT_ID))).willReturn(confirmedResponse());

            mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.enrollmentId").value(101))
                    .andExpect(jsonPath("$.data.userId").value(USER_ID))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.data.confirmedAt").exists())
                    .andExpect(jsonPath("$.data.cancelledAt").doesNotExist())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("X-User-Role이 ROLE_CREATOR이면 403 FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
        void confirm_forbiddenForRoleCreator() throws Exception {
            mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.error.code").value(140301))
                    .andExpect(jsonPath("$.error.message").value("FORBIDDEN"));

            verify(enrollmentService, never()).confirm(any(), any());
        }

        @Test
        @DisplayName("서비스가 ENROLLMENT_NOT_FOUND를 던지면 404와 해당 에러 코드를 반환한다")
        void confirm_notFound() throws Exception {
            given(enrollmentService.confirm(eq(USER_ID), eq(ENROLLMENT_ID)))
                    .willThrow(new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

            mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.error.code").value(340401))
                    .andExpect(jsonPath("$.error.message").value("ENROLLMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("서비스가 INVALID_STATUS_FOR_CONFIRM을 던지면 409와 해당 에러 코드를 반환한다")
        void confirm_invalidStatus() throws Exception {
            given(enrollmentService.confirm(eq(USER_ID), eq(ENROLLMENT_ID)))
                    .willThrow(new BusinessException(ErrorCode.INVALID_STATUS_FOR_CONFIRM));

            mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.error.code").value(340904))
                    .andExpect(jsonPath("$.error.message").value("INVALID_STATUS_FOR_CONFIRM"));
        }

        @Test
        @DisplayName("서비스가 PAYMENT_DEADLINE_EXPIRED를 던지면 409와 해당 에러 코드를 반환한다")
        void confirm_deadlineExpired() throws Exception {
            given(enrollmentService.confirm(eq(USER_ID), eq(ENROLLMENT_ID)))
                    .willThrow(new BusinessException(ErrorCode.PAYMENT_DEADLINE_EXPIRED));

            mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.error.code").value(340903))
                    .andExpect(jsonPath("$.error.message").value("PAYMENT_DEADLINE_EXPIRED"));
        }

        @Test
        @DisplayName("인증 헤더가 누락되면 기존 핸들러가 USER_NOT_FOUND로 변환해 응답한다")
        void confirm_missingAuthHeader() throws Exception {
            mockMvc.perform(post("/enrollments/{enrollmentId}/confirm", ENROLLMENT_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(140003))
                    .andExpect(jsonPath("$.error.message").value("USER_NOT_FOUND"));

            verify(enrollmentService, never()).confirm(any(), any());
        }
    }

    @Nested
    @DisplayName("POST /enrollments/{enrollmentId}/cancel")
    class Cancel {

        private EnrollmentResponse cancelledResponse() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 5, 24, 14, 15);
            LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 24, 14, 30);
            LocalDateTime cancelledAt = LocalDateTime.of(2026, 5, 24, 14, 45);
            return new EnrollmentResponse(
                    ENROLLMENT_ID, USER_ID, COURSE_ID, EnrollmentStatus.CANCELLED,
                    confirmedAt, cancelledAt, createdAt, cancelledAt);
        }

        @Test
        @DisplayName("ROLE_USER가 수강 취소에 성공하면 200과 CANCELLED 신청 정보를 반환한다")
        void cancel_returns200() throws Exception {
            given(enrollmentService.cancel(eq(USER_ID), eq(ENROLLMENT_ID))).willReturn(cancelledResponse());

            mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.enrollmentId").value(101))
                    .andExpect(jsonPath("$.data.userId").value(USER_ID))
                    .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.data.cancelledAt").exists())
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("X-User-Role이 ROLE_CREATOR이면 403 FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
        void cancel_forbiddenForRoleCreator() throws Exception {
            mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.error.code").value(140301))
                    .andExpect(jsonPath("$.error.message").value("FORBIDDEN"));

            verify(enrollmentService, never()).cancel(any(), any());
        }

        @Test
        @DisplayName("서비스가 ENROLLMENT_NOT_FOUND를 던지면 404와 해당 에러 코드를 반환한다")
        void cancel_notFound() throws Exception {
            given(enrollmentService.cancel(eq(USER_ID), eq(ENROLLMENT_ID)))
                    .willThrow(new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

            mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.error.code").value(340401))
                    .andExpect(jsonPath("$.error.message").value("ENROLLMENT_NOT_FOUND"));
        }

        @Test
        @DisplayName("서비스가 INVALID_STATUS_FOR_CANCEL을 던지면 409와 해당 에러 코드를 반환한다")
        void cancel_invalidStatus() throws Exception {
            given(enrollmentService.cancel(eq(USER_ID), eq(ENROLLMENT_ID)))
                    .willThrow(new BusinessException(ErrorCode.INVALID_STATUS_FOR_CANCEL));

            mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.error.code").value(340905))
                    .andExpect(jsonPath("$.error.message").value("INVALID_STATUS_FOR_CANCEL"));
        }

        @Test
        @DisplayName("서비스가 REFUND_WINDOW_EXPIRED를 던지면 409와 해당 에러 코드를 반환한다")
        void cancel_refundWindowExpired() throws Exception {
            given(enrollmentService.cancel(eq(USER_ID), eq(ENROLLMENT_ID)))
                    .willThrow(new BusinessException(ErrorCode.REFUND_WINDOW_EXPIRED));

            mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", ENROLLMENT_ID)
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value(409))
                    .andExpect(jsonPath("$.error.code").value(340906))
                    .andExpect(jsonPath("$.error.message").value("REFUND_WINDOW_EXPIRED"));
        }

        @Test
        @DisplayName("인증 헤더가 누락되면 기존 핸들러가 USER_NOT_FOUND로 변환해 응답한다")
        void cancel_missingAuthHeader() throws Exception {
            mockMvc.perform(post("/enrollments/{enrollmentId}/cancel", ENROLLMENT_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(140003))
                    .andExpect(jsonPath("$.error.message").value("USER_NOT_FOUND"));

            verify(enrollmentService, never()).cancel(any(), any());
        }
    }

    @Nested
    @DisplayName("GET /enrollments/me")
    class GetMyEnrollments {

        private PageMyEnrollmentItem onePage() {
            LocalDateTime createdAt = LocalDateTime.of(2026, 5, 24, 14, 15);
            MyEnrollmentItemResponse item = new MyEnrollmentItemResponse(
                    ENROLLMENT_ID, COURSE_ID, "Spring Boot 입문", 49000, 30,
                    EnrollmentStatus.PENDING, null, null, createdAt);
            return new PageMyEnrollmentItem(List.of(item), 0, 20, 1, 1);
        }

        @Test
        @DisplayName("ROLE_USER가 조회하면 200과 내 신청 목록 페이지를 반환한다")
        void getMyEnrollments_returns200() throws Exception {
            given(enrollmentService.getMyEnrollments(eq(USER_ID), any(Pageable.class))).willReturn(onePage());

            mockMvc.perform(get("/enrollments/me")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.content[0].enrollmentId").value(101))
                    .andExpect(jsonPath("$.data.content[0].courseId").value(1))
                    .andExpect(jsonPath("$.data.content[0].courseTitle").value("Spring Boot 입문"))
                    .andExpect(jsonPath("$.data.content[0].status").value("PENDING"))
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.error").doesNotExist());
        }

        @Test
        @DisplayName("X-User-Role이 ROLE_CREATOR이면 403 FORBIDDEN을 반환하고 서비스는 호출되지 않는다")
        void getMyEnrollments_forbiddenForRoleCreator() throws Exception {
            mockMvc.perform(get("/enrollments/me")
                            .header("X-User-Id", USER_ID)
                            .header("X-User-Role", "ROLE_CREATOR"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.error.code").value(140301))
                    .andExpect(jsonPath("$.error.message").value("FORBIDDEN"));

            verify(enrollmentService, never()).getMyEnrollments(any(), any());
        }

        @Test
        @DisplayName("인증 헤더가 누락되면 기존 핸들러가 USER_NOT_FOUND로 변환해 응답한다")
        void getMyEnrollments_missingAuthHeader() throws Exception {
            mockMvc.perform(get("/enrollments/me")
                            .header("X-User-Role", "ROLE_USER"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value(140003))
                    .andExpect(jsonPath("$.error.message").value("USER_NOT_FOUND"));

            verify(enrollmentService, never()).getMyEnrollments(any(), any());
        }
    }
}
