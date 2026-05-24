package com.futureschole.course.controller;

import com.futureschole.course.common.ApiResponse;
import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.entity.type.EnrollmentResultType;
import com.futureschole.course.service.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
