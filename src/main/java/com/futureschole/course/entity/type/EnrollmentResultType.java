package com.futureschole.course.entity.type;

/**
 * 수강 신청 결과 타입.
 *
 * <p>신청 API 응답에서 결과를 구분하는 판별자다. {@link #ENROLLED}는 정원 내 신청 성공({@code PENDING}
 * 생성), {@link #WAITLISTED}는 정원 마감으로 대기열에 진입한 경우다. 영속 대상이 아니라 응답 표현에만 쓰인다.
 */
public enum EnrollmentResultType {

    /** 정원 내 신청 성공. {@code PENDING} 신청이 생성되고 결제 기한이 시작된다. */
    ENROLLED,

    /** 정원 마감으로 대기열에 진입. 대기 순번이 함께 반환된다. */
    WAITLISTED
}
