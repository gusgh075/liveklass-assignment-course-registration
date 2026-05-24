package com.futureschole.course.dto.response;

import com.futureschole.course.entity.Course;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 강의 목록 페이지 응답.
 *
 * <p>OpenAPI {@code PageCourseSummary} 스키마와 1:1 대응한다. Spring Data의 {@link Page} 메타데이터
 * (현재 페이지 번호·크기·전체 건수·전체 페이지 수)를 명세에 맞는 평면 구조로 옮겨 담는다.
 *
 * @param content       현재 페이지의 강의 목록 항목들
 * @param page          0-based 현재 페이지 번호
 * @param size          페이지 크기
 * @param totalElements 필터 조건을 만족하는 전체 강의 수
 * @param totalPages    전체 페이지 수
 */
public record PageCourseSummary(
        List<CourseSummaryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * 강의 페이지와 항목 변환 함수로 목록 페이지 응답을 만든다.
     *
     * <p>각 강의를 {@code mapper}로 항목 응답으로 변환하므로, 인원 카운트 같은 강의별 부가 정보는
     * 호출 측이 변환 함수 안에서 채워 넣는다. 페이지 메타데이터는 {@link Page}에서 그대로 옮긴다.
     *
     * @param page   조회된 강의 페이지
     * @param mapper 강의 한 건을 목록 항목 응답으로 변환하는 함수
     * @return 강의 목록 페이지 응답
     */
    public static PageCourseSummary from(Page<Course> page, Function<Course, CourseSummaryResponse> mapper) {
        List<CourseSummaryResponse> content = page.getContent().stream()
                .map(mapper)
                .toList();
        return new PageCourseSummary(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
