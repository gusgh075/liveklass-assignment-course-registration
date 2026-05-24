package com.futureschole.course.dto.response;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.type.CourseStatus;

import java.time.LocalDateTime;

/**
 * 강의 목록 항목 응답.
 *
 * <p>OpenAPI {@code CourseSummaryResponse} 스키마와 1:1 대응한다. 목록 조회에서 강의 한 건을 표현하며,
 * 상세 응답과 달리 설명·작성자·생성/수정 시각은 담지 않는다.
 *
 * <p>{@code enrolledCount}는 활성 신청 인원({@code PENDING + CONFIRMED} 합계), {@code waitingCount}는
 * 대기열 인원 수다. 두 값은 목록 페이지의 강의들을 한 번에 묶어 집계한 결과를 호출 측이 전달한다.
 */
public record CourseSummaryResponse(
        Long id,
        String title,
        int price,
        int capacity,
        int enrolledCount,
        int waitingCount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        CourseStatus status
) {

    /**
     * {@link Course} 엔티티와 인원 카운트를 합쳐 목록 항목 응답을 만든다.
     *
     * @param course        강의 엔티티
     * @param enrolledCount 활성 신청 인원({@code PENDING + CONFIRMED})
     * @param waitingCount  대기열 인원 수
     * @return 강의 목록 항목 응답
     */
    public static CourseSummaryResponse from(Course course, int enrolledCount, int waitingCount) {
        return new CourseSummaryResponse(
                course.getId(),
                course.getTitle(),
                course.getPrice(),
                course.getCapacity(),
                enrolledCount,
                waitingCount,
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus()
        );
    }
}
