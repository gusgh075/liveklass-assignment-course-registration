package com.futureschole.course.entity.type;

/**
 * 사용자 역할.
 *
 * <p>{@code X-User-Role} 헤더로 전달되는 인증 컨텍스트 값과 동일하다.
 */
public enum UserRole {

    /** 수강생. 강의 목록·상세 조회, 수강 신청·결제·취소, 본인 신청 목록 조회. */
    ROLE_USER,

    /** 강사(크리에이터). 강의 등록·상태 변경·수강생 목록 조회. */
    ROLE_CREATOR
}
