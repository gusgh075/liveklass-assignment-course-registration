package com.futureschole.course.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 에러 식별 코드 정의.
 *
 * <p>{@link ApiResponse}의 {@code error.code}에 들어가는 6자리 정수와 HTTP 상태,
 * 기본 사용자 메시지를 한 값으로 묶어 정의한다. 코드 부여 규칙과 전체 표는
 * {@code docs/error-codes.md}에 단일 진실원천으로 정리되어 있다.
 *
 * <p>본 초기 세팅에서는 도메인에 귀속되지 않는 공통 코드({@code 1xxxxx})만 포함한다.
 * Course({@code 2xxxxx})·Enrollment({@code 3xxxxx}) 도메인 코드는 해당 도메인을
 * 구현하는 이슈에서 표를 보고 항목을 추가한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ============ 공통 (1xxxxx) ============

    /** 요청 본문·파라미터 검증 실패. {@code error.details}에 필드별 배열을 채운다. */
    VALIDATION_FAILED(140001, HttpStatus.BAD_REQUEST, "요청이 유효하지 않습니다."),

    /** JSON 파싱 실패(잘못된 본문 형식). */
    MALFORMED_JSON(140002, HttpStatus.BAD_REQUEST, "요청 본문 형식이 잘못되었습니다."),

    /** 사용자 식별 헤더가 누락되었거나 존재하지 않는 사용자. */
    USER_NOT_FOUND(140003, HttpStatus.BAD_REQUEST, "사용자 정보를 확인할 수 없습니다."),

    /** 역할 불일치 또는 리소스 소유권 부재. 도메인별 더 구체적인 코드가 있으면 그것을 우선. */
    FORBIDDEN(140301, HttpStatus.FORBIDDEN, "요청 권한이 없습니다."),

    /** 일반 catch-all 404. 도메인별 NOT_FOUND가 있으면 그것을 우선. */
    RESOURCE_NOT_FOUND(140401, HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    /** 미처리 예외에 대한 fallback. */
    INTERNAL_SERVER_ERROR(150001, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    /** 6자리 도메인 에러 식별 정수. {@code D HHH SS}(도메인 prefix·HTTP 상태·일련번호) 결합. */
    private final int code;

    /** 이 에러에 대응하는 HTTP 상태값. */
    private final HttpStatus httpStatus;

    /** 컨텍스트가 없을 때 사용할 기본 사용자 메시지. */
    private final String defaultMessage;
}