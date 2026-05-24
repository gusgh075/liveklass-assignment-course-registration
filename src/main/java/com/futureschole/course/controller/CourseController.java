package com.futureschole.course.controller;

import com.futureschole.course.common.ApiResponse;
import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "강의 등록·조회·상태 변경, 강의별 수강생 조회")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(
            summary = "강의 등록 (DRAFT 생성)",
            description = "ROLE_CREATOR가 강의 정보를 임시저장한다. 생성 시점에 강의 ID가 부여되며 상태는 DRAFT로 시작한다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<CourseDetailResponse>> create(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @Valid @RequestBody CourseCreateRequest request) {

        if (!"ROLE_CREATOR".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        CourseDetailResponse data = courseService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, data, "강의가 생성되었습니다."));
    }

    @Operation(
            summary = "강의 정보 수정 (DRAFT만)",
            description = "ROLE_CREATOR가 본인의 DRAFT 강의 정보를 전체 교체한다. OPEN/CLOSED 상태에서는 409로 거부한다."
    )
    @PutMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> update(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long courseId,
            @Valid @RequestBody CourseCreateRequest request) {

        if (!"ROLE_CREATOR".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        CourseDetailResponse data = courseService.update(userId, courseId, request);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "강의가 수정되었습니다."));
    }
}
