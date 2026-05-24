package com.futureschole.course.controller;

import com.futureschole.course.common.ApiResponse;
import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.dto.response.EnrollmentResponse;
import com.futureschole.course.dto.response.PageMyEnrollmentItem;
import com.futureschole.course.entity.type.EnrollmentResultType;
import com.futureschole.course.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enrollment", description = "수강 신청(대기열 통합)·결제 확정·취소·내 신청 목록")
@RestController
@RequestMapping("/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @Operation(
            summary = "수강 신청 (대기열 자동 진입)",
            description = "ROLE_USER가 강의에 수강 신청한다. 정원에 자리가 있으면 즉시 PENDING 신청을 생성(결제 기한 30분 시작)하고, 자리가 없으면 자동으로 대기열에 진입한다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<EnrollmentCreateResponse>> apply(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody EnrollmentCreateRequest request) {

        if (!"ROLE_USER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        EnrollmentCreateResponse data = enrollmentService.apply(userId, request);
        String message = data.resultType() == EnrollmentResultType.ENROLLED
                ? "신청이 접수되었습니다. 30분 이내 결제해 주세요."
                : "정원이 마감되어 대기열에 진입했습니다.";
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, data, message));
    }

    @Operation(
            summary = "결제 확정",
            description = "ROLE_USER가 본인의 PENDING 신청을 CONFIRMED로 전이한다. 결제 기한 30분이 지나지 않은 신청만 확정할 수 있으며, 확정 시각이 기록되어 7일 환불 기준이 된다."
    )
    @PostMapping("/{enrollmentId}/confirm")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> confirm(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long enrollmentId) {

        if (!"ROLE_USER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        EnrollmentResponse data = enrollmentService.confirm(userId, enrollmentId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "결제가 확정되었습니다."));
    }

    @Operation(
            summary = "수강 취소",
            description = "ROLE_USER가 본인의 CONFIRMED 신청을 CANCELLED로 전이한다. 결제 확정 시각으로부터 7일 이내인 신청만 취소할 수 있으며, 취소 시각이 기록되고 정원 산정에서 제외된다."
    )
    @PostMapping("/{enrollmentId}/cancel")
    public ResponseEntity<ApiResponse<EnrollmentResponse>> cancel(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long enrollmentId) {

        if (!"ROLE_USER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        EnrollmentResponse data = enrollmentService.cancel(userId, enrollmentId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "수강 신청이 취소되었습니다."));
    }

    @Operation(
            summary = "내 수강 신청 목록 조회",
            description = "ROLE_USER가 본인의 수강 신청 이력을 페이지네이션으로 조회한다. PENDING/CONFIRMED/CANCELLED 모든 상태가 포함되며, 각 항목에 강의 제목·가격·정원이 함께 담긴다."
    )
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<PageMyEnrollmentItem>> getMyEnrollments(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PageableDefault(size = 20) Pageable pageable) {

        if (!"ROLE_USER".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        PageMyEnrollmentItem data = enrollmentService.getMyEnrollments(userId, pageable);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "내 수강 신청 목록 조회에 성공했습니다."));
    }
}
