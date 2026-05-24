package com.futureschole.course.dto.response;

import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.type.EnrollmentStatus;

import java.time.LocalDateTime;

/**
 * 단일 수강 신청 상태 응답.
 *
 * <p>OpenAPI {@code EnrollmentResponse} 스키마와 1:1 대응한다. 결제 확정·취소 전이 결과로 신청의
 * 현재 상태를 반환할 때 사용하며, {@code userId}는 내부 PK가 아닌 외부 식별자다.
 *
 * @param userId    신청 사용자의 외부 식별자
 * @param status    현재 신청 상태
 * @param confirmedAt 결제 확정 시각. 확정 전에는 {@code null}.
 * @param cancelledAt 취소 시각. 취소 전에는 {@code null}.
 */
public record EnrollmentResponse(
        Long enrollmentId,
        String userId,
        Long courseId,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * 수강 신청 엔티티를 응답 DTO로 변환한다.
     *
     * @param enrollment 변환 대상 수강 신청
     * @return 엔티티의 현재 상태를 담은 응답
     */
    public static EnrollmentResponse from(Enrollment enrollment) {
        return new EnrollmentResponse(
                enrollment.getId(),
                enrollment.getUser().getUserId(),
                enrollment.getCourse().getId(),
                enrollment.getStatus(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt(),
                enrollment.getCreatedAt(),
                enrollment.getUpdatedAt());
    }
}
