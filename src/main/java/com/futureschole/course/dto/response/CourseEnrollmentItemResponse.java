package com.futureschole.course.dto.response;

import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.type.EnrollmentStatus;

import java.time.LocalDateTime;

/**
 * 강의별 수강생 목록 항목 응답.
 *
 * <p>OpenAPI {@code CourseEnrollmentItemResponse} 스키마와 1:1 대응한다. 강사가 자신의 강의에
 * 신청한 수강생 한 명을 표현하며, 활성 신청({@code PENDING}/{@code CONFIRMED})만 목록에 담긴다.
 *
 * <p>{@code userId}는 신청 사용자의 외부 식별자다. {@code confirmedAt}은 결제 확정 시각으로,
 * 아직 결제 대기 중인 {@code PENDING} 신청에서는 비어 있다.
 */
public record CourseEnrollmentItemResponse(
        Long enrollmentId,
        String userId,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime createdAt
) {

    /**
     * {@link Enrollment} 엔티티를 강의별 수강생 목록 항목 응답으로 변환한다.
     *
     * <p>사용자 외부 식별자는 연관된 사용자에서 꺼내 담으므로, 호출 측은 사용자 연관이 함께 로드된
     * 신청을 전달해야 한다.
     *
     * @param enrollment 변환할 수강 신청 엔티티
     * @return 수강생 목록 항목 응답
     */
    public static CourseEnrollmentItemResponse from(Enrollment enrollment) {
        return new CourseEnrollmentItemResponse(
                enrollment.getId(),
                enrollment.getUser().getUserId(),
                enrollment.getStatus(),
                enrollment.getConfirmedAt(),
                enrollment.getCreatedAt()
        );
    }
}
