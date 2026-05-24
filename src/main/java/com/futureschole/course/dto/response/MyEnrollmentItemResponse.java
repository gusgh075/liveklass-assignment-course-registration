package com.futureschole.course.dto.response;

import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.type.EnrollmentStatus;

import java.time.LocalDateTime;

/**
 * 내 수강 신청 목록 항목 응답.
 *
 * <p>OpenAPI {@code MyEnrollmentItemResponse} 스키마와 1:1 대응한다. 본인의 신청 이력 한 건을
 * 표현하며, 신청 상태와 함께 대상 강의의 제목·가격·정원을 함께 담는다. {@code PENDING}/{@code CONFIRMED}/
 * {@code CANCELLED} 모든 상태가 목록에 포함된다.
 *
 * @param courseTitle 신청한 강의 제목
 * @param confirmedAt 결제 확정 시각. 확정 전에는 {@code null}.
 * @param cancelledAt 취소 시각. 취소 전에는 {@code null}.
 */
public record MyEnrollmentItemResponse(
        Long enrollmentId,
        Long courseId,
        String courseTitle,
        int price,
        int capacity,
        EnrollmentStatus status,
        LocalDateTime confirmedAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt
) {

    /**
     * 수강 신청 엔티티를 내 신청 목록 항목 응답으로 변환한다.
     *
     * <p>강의 제목·가격·정원은 연관된 {@link Enrollment#getCourse()}에서 가져온다. N+1 회피를 위해
     * Repository에서 강의를 함께 로드한 상태로 호출되는 것을 전제로 한다.
     *
     * @param enrollment 변환 대상 수강 신청
     * @return 강의 정보가 결합된 내 신청 목록 항목 응답
     */
    public static MyEnrollmentItemResponse from(Enrollment enrollment) {
        return new MyEnrollmentItemResponse(
                enrollment.getId(),
                enrollment.getCourse().getId(),
                enrollment.getCourse().getTitle(),
                enrollment.getCourse().getPrice(),
                enrollment.getCourse().getCapacity(),
                enrollment.getStatus(),
                enrollment.getConfirmedAt(),
                enrollment.getCancelledAt(),
                enrollment.getCreatedAt());
    }
}
