package com.futureschole.course.dto.response;

import com.futureschole.course.entity.type.EnrollmentResultType;
import com.futureschole.course.entity.type.EnrollmentStatus;

import java.time.LocalDateTime;

/**
 * 수강 신청 결과 응답.
 *
 * <p>OpenAPI {@code EnrollmentCreateResponse} 스키마와 1:1 대응한다. {@code resultType}으로 결과를
 * 구분하며, {@code ENROLLED}이면 {@code enrollmentId}/{@code status}/{@code paymentDeadline}이,
 * {@code WAITLISTED}이면 {@code waitlistId}/{@code waitlistPosition}이 채워진다. 해당하지 않는 필드는
 * {@code null}이다. 두 결과를 각각 {@link #enrolled}/{@link #waitlisted} 정적 팩토리로 만든다.
 */
public record EnrollmentCreateResponse(
        EnrollmentResultType resultType,
        Long enrollmentId,
        Long courseId,
        EnrollmentStatus status,
        LocalDateTime paymentDeadline,
        Long waitlistId,
        Integer waitlistPosition
) {

    /**
     * 정원 내 신청 성공 응답을 만든다.
     *
     * @param enrollmentId    생성된 수강 신청 식별자
     * @param courseId        신청 대상 강의 식별자
     * @param paymentDeadline 결제 기한(신청 생성 시각 + 30분)
     * @return {@code ENROLLED} 결과 응답({@code status}는 {@code PENDING})
     */
    public static EnrollmentCreateResponse enrolled(Long enrollmentId, Long courseId, LocalDateTime paymentDeadline) {
        return new EnrollmentCreateResponse(
                EnrollmentResultType.ENROLLED, enrollmentId, courseId,
                EnrollmentStatus.PENDING, paymentDeadline, null, null);
    }

    /**
     * 대기열 진입 응답을 만든다.
     *
     * @param waitlistId       생성된 대기열 레코드 식별자
     * @param courseId         대기 대상 강의 식별자
     * @param waitlistPosition 1-based 대기 순번
     * @return {@code WAITLISTED} 결과 응답
     */
    public static EnrollmentCreateResponse waitlisted(Long waitlistId, Long courseId, int waitlistPosition) {
        return new EnrollmentCreateResponse(
                EnrollmentResultType.WAITLISTED, null, courseId,
                null, null, waitlistId, waitlistPosition);
    }
}
