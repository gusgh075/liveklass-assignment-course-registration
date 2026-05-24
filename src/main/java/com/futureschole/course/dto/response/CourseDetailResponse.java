package com.futureschole.course.dto.response;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.type.CourseStatus;

import java.time.LocalDateTime;

/**
 * 강의 상세 응답.
 *
 * <p>OpenAPI {@code CourseDetailResponse} 스키마와 1:1 대응한다. 강의 등록·수정·상태 변경 등
 * 강의 단건을 반환하는 응답에 공통으로 사용된다. {@code creatorId}는 내부 PK가 아닌 외부 식별자
 * ({@code User.userId})를 담는다.
 *
 * <p>{@code enrolledCount}는 {@code PENDING + CONFIRMED}의 합계이며 {@code waitingCount}는
 * 대기열 인원 수다. 생성 직후에는 두 값 모두 0이고, 그 외 시점에는 호출 측이 집계 결과를 전달한다.
 */
public record CourseDetailResponse(
        Long id,
        String creatorId,
        String title,
        String description,
        int price,
        int capacity,
        int enrolledCount,
        int waitingCount,
        LocalDateTime startDate,
        LocalDateTime endDate,
        CourseStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * {@link Course} 엔티티와 인원 카운트를 합쳐 응답 객체를 만든다.
     *
     * @param course         강의 엔티티
     * @param enrolledCount  활성 신청 인원({@code PENDING + CONFIRMED})
     * @param waitingCount   대기열 인원 수
     * @return 강의 상세 응답
     */
    public static CourseDetailResponse from(Course course, int enrolledCount, int waitingCount) {
        return new CourseDetailResponse(
                course.getId(),
                course.getCreator().getUserId(),
                course.getTitle(),
                course.getDescription(),
                course.getPrice(),
                course.getCapacity(),
                enrolledCount,
                waitingCount,
                course.getStartDate(),
                course.getEndDate(),
                course.getStatus(),
                course.getCreatedAt(),
                course.getUpdatedAt()
        );
    }
}
