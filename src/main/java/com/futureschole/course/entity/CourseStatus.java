package com.futureschole.course.entity;

/**
 * 강의 상태.
 *
 * <p>전이 흐름은 {@code DRAFT → OPEN → CLOSED}이며, 단방향이다.
 */
public enum CourseStatus {

    /** 임시저장. 신청 불가. 크리에이터만 접근. */
    DRAFT,

    /** 모집 중. 신청 가능. 정원이 차도 본 상태를 유지하고 대기열 진입을 허용한다. */
    OPEN,

    /** 모집 마감. 신청 불가. 종료일 경과 자동 마감 또는 크리에이터의 명시적 마감으로만 진입. */
    CLOSED
}
