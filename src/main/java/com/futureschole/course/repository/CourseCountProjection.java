package com.futureschole.course.repository;

/**
 * 강의별 집계 결과를 담는 인터페이스 프로젝션.
 *
 * <p>여러 강의의 신청 인원·대기 인원을 한 번의 쿼리로 묶어 집계할 때 {@code (강의 ID, 건수)} 쌍을
 * 행 단위로 받기 위해 사용한다. 호출 측은 이 결과를 {@code Map<Long, Long>}으로 모아 강의별 카운트를
 * 조회한다.
 */
public interface CourseCountProjection {

    /** 집계 대상 강의의 식별자. */
    Long getCourseId();

    /** 해당 강의의 집계 건수(활성 신청 수 또는 대기열 인원 수). */
    long getCount();
}
