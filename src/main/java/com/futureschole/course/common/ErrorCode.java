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
 * <p>공통 코드({@code 1xxxxx})와 현재까지 구현된 API가 사용하는 Course({@code 2xxxxx}) 코드를
 * 포함한다. 아직 쓰이지 않는 Course 코드와 Enrollment({@code 3xxxxx}) 도메인 코드는 해당 코드를
 * 처음 던지는 API를 구현하는 이슈에서 표를 보고 항목을 추가한다.
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
    INTERNAL_SERVER_ERROR(150001, HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    // ============ Course (2xxxxx) ============

    /** 지정된 {@code courseId}에 해당하는 강의가 없음. */
    COURSE_NOT_FOUND(240401, HttpStatus.NOT_FOUND, "강의를 찾을 수 없습니다."),

    /** 본인이 작성한 강의가 아닌 강의에 대한 수정·상태 변경·수강생 목록 조회 시도. */
    COURSE_NOT_OWNED(240301, HttpStatus.FORBIDDEN, "본인이 작성한 강의가 아닙니다."),

    /** {@code DRAFT} 이외 상태의 강의를 수정 시도. */
    COURSE_NOT_EDITABLE(240902, HttpStatus.CONFLICT, "DRAFT 상태의 강의만 수정할 수 있습니다."),

    // ============ Enrollment (3xxxxx) ============

    /** 지정된 {@code enrollmentId}에 해당하는 수강 신청이 없음. */
    ENROLLMENT_NOT_FOUND(340401, HttpStatus.NOT_FOUND, "수강 신청을 찾을 수 없습니다."),

    /** 본인의 신청이 아닌 신청에 대한 결제 확정·취소 시도. */
    ENROLLMENT_NOT_OWNED(340301, HttpStatus.FORBIDDEN, "본인의 수강 신청이 아닙니다."),

    /** 동일 강의에 활성 신청(`PENDING`/`CONFIRMED`)이나 대기열 진입이 이미 존재. */
    DUPLICATE_ACTIVE_ENROLLMENT(340901, HttpStatus.CONFLICT, "이미 활성 상태의 수강 신청이 존재합니다."),

    /** 강의 상태가 {@code OPEN}이 아니라 신청을 받을 수 없음. */
    COURSE_NOT_OPEN_FOR_ENROLLMENT(340902, HttpStatus.CONFLICT, "현재 신청을 받지 않는 강의입니다."),

    /** 결제 기한 30분이 경과한 {@code PENDING}에 대한 결제 확정 시도. */
    PAYMENT_DEADLINE_EXPIRED(340903, HttpStatus.CONFLICT, "결제 기한이 만료되었습니다."),

    /** 결제 확정 대상이 {@code PENDING} 상태가 아님(이미 {@code CONFIRMED}이거나 {@code CANCELLED}). */
    INVALID_STATUS_FOR_CONFIRM(340904, HttpStatus.CONFLICT, "결제 확정할 수 없는 상태의 신청입니다."),

    /** 수강 취소 대상이 {@code CONFIRMED} 상태가 아님(예: {@code PENDING}·{@code CANCELLED}). */
    INVALID_STATUS_FOR_CANCEL(340905, HttpStatus.CONFLICT, "취소할 수 없는 상태의 신청입니다."),

    /** 결제 확정 후 7일이 경과한 신청에 대한 수강 취소 시도. */
    REFUND_WINDOW_EXPIRED(340906, HttpStatus.CONFLICT, "결제 후 7일이 경과하여 수강 취소가 불가합니다.");

    /** 6자리 도메인 에러 식별 정수. {@code D HHH SS}(도메인 prefix·HTTP 상태·일련번호) 결합. */
    private final int code;

    /** 이 에러에 대응하는 HTTP 상태값. */
    private final HttpStatus httpStatus;

    /** 컨텍스트가 없을 때 사용할 기본 사용자 메시지. */
    private final String defaultMessage;
}