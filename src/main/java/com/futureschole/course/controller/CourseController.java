package com.futureschole.course.controller;

import com.futureschole.course.common.ApiResponse;
import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.request.CourseStatusChangeRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.dto.response.PageCourseEnrollmentItem;
import com.futureschole.course.dto.response.PageCourseSummary;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Course", description = "강의 등록·조회·상태 변경, 강의별 수강생 조회")
@RestController
@RequestMapping("/courses")
@RequiredArgsConstructor
public class CourseController {

    /** 목록 조회에서 {@code status} 파라미터가 없을 때 적용하는 기본 필터. {@code DRAFT}는 제외한다. */
    private static final List<CourseStatus> DEFAULT_LIST_STATUSES =
            List.of(CourseStatus.OPEN, CourseStatus.CLOSED);

    private final CourseService courseService;

    @Operation(
            summary = "강의 목록 조회",
            description = "강의 목록을 페이지네이션으로 조회한다. status로 필터하며 미지정 시 기본은 OPEN+CLOSED이고 DRAFT는 제외한다. 인증 헤더는 필요하지 않다."
    )
    @GetMapping
    public ResponseEntity<ApiResponse<PageCourseSummary>> getList(
            @RequestParam(required = false) List<CourseStatus> status,
            @PageableDefault(size = 20) Pageable pageable) {

        List<CourseStatus> statuses = (status == null || status.isEmpty()) ? DEFAULT_LIST_STATUSES : status;
        PageCourseSummary data = courseService.getList(statuses, pageable);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "강의 목록 조회에 성공했습니다."));
    }

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

    @Operation(
            summary = "강의 상태 변경 (오픈·마감)",
            description = "ROLE_CREATOR가 본인의 강의를 오픈(DRAFT→OPEN) 또는 마감(OPEN→CLOSED)한다. 허용되지 않은 전이나 종료일이 지난 강의의 오픈은 409로 거부한다."
    )
    @PatchMapping("/{courseId}/status")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> changeStatus(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long courseId,
            @Valid @RequestBody CourseStatusChangeRequest request) {

        if (!"ROLE_CREATOR".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        CourseDetailResponse data = courseService.changeStatus(userId, courseId, request.status());
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "강의 상태가 변경되었습니다."));
    }

    @Operation(
            summary = "강의 상세 조회",
            description = "강의 단건의 상세 정보를 반환한다. 인증 없이 누구나 조회할 수 있으며, 현재 신청 인원(PENDING+CONFIRMED)과 대기 인원이 함께 담긴다."
    )
    @GetMapping("/{courseId}")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getDetail(@PathVariable Long courseId) {
        CourseDetailResponse data = courseService.getDetail(courseId);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "강의 상세 조회에 성공했습니다."));
    }

    @Operation(
            summary = "강의별 수강생 목록 조회 (강사용)",
            description = "ROLE_CREATOR가 본인 강의의 활성 수강생(PENDING·CONFIRMED)을 페이지네이션으로 조회한다. 본인이 작성한 강의가 아니면 403으로 거부한다."
    )
    @GetMapping("/{courseId}/enrollments")
    public ResponseEntity<ApiResponse<PageCourseEnrollmentItem>> getCourseEnrollments(
            @RequestHeader("X-User-Id") String userId,
            @RequestHeader("X-User-Role") String role,
            @PathVariable Long courseId,
            @PageableDefault(size = 20) Pageable pageable) {

        if (!"ROLE_CREATOR".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        PageCourseEnrollmentItem data = courseService.getCourseEnrollments(courseId, userId, pageable);
        return ResponseEntity.status(HttpStatus.OK)
                .body(ApiResponse.success(HttpStatus.OK, data, "수강생 목록 조회에 성공했습니다."));
    }
}
