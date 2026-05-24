package com.futureschole.course.dto.response;

import com.futureschole.course.entity.Enrollment;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 강의별 수강생 목록 페이지 응답.
 *
 * <p>OpenAPI {@code PageCourseEnrollmentItem} 스키마와 1:1 대응한다. Spring Data의 {@link Page}
 * 메타데이터(현재 페이지 번호·크기·전체 건수·전체 페이지 수)를 명세에 맞는 평면 구조로 옮겨 담는다.
 *
 * @param content       현재 페이지의 수강생 목록 항목들
 * @param page          0-based 현재 페이지 번호
 * @param size          페이지 크기
 * @param totalElements 강의의 활성 신청 전체 수
 * @param totalPages    전체 페이지 수
 */
public record PageCourseEnrollmentItem(
        List<CourseEnrollmentItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * 수강 신청 페이지와 항목 변환 함수로 수강생 목록 페이지 응답을 만든다.
     *
     * <p>각 신청을 {@code mapper}로 항목 응답으로 변환하며, 페이지 메타데이터는 {@link Page}에서
     * 그대로 옮긴다.
     *
     * @param page   조회된 수강 신청 페이지
     * @param mapper 수강 신청 한 건을 목록 항목 응답으로 변환하는 함수
     * @return 수강생 목록 페이지 응답
     */
    public static PageCourseEnrollmentItem from(
            Page<Enrollment> page, Function<Enrollment, CourseEnrollmentItemResponse> mapper) {
        List<CourseEnrollmentItemResponse> content = page.getContent().stream()
                .map(mapper)
                .toList();
        return new PageCourseEnrollmentItem(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
