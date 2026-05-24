package com.futureschole.course.entity.type;

/**
 * 수강 신청 상태.
 *
 * <p>전이 흐름은 {@code PENDING → CONFIRMED → CANCELLED}이다. 정원 산정에는 {@code PENDING}과
 * {@code CONFIRMED}만 포함하고 {@code CANCELLED}는 제외한다.
 */
public enum EnrollmentStatus {

    /** 신청 완료, 결제 대기. 생성 후 30분 안에 결제하지 않으면 취소된다. */
    PENDING,

    /** 결제 완료, 수강 확정. */
    CONFIRMED,

    /** 취소됨. 정원 산정에서 제외되며, 동일 강의 재신청을 막지 않는다. */
    CANCELLED
}
