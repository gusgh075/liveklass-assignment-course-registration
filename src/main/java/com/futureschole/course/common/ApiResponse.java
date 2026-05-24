package com.futureschole.course.common;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 모든 API 응답을 감싸는 표준 래퍼.
 *
 * <p>OpenAPI 명세({@code docs/openapi.yml})에 정의된 공통 응답 형식이다. 성공 응답은
 * {@code data}에 실제 데이터를 담고 {@code error}는 {@code null}이며, 실패 응답은 그 반대이다.
 *
 * <p>본문의 {@code code} 필드는 실제 HTTP 상태값과 항상 일치하도록 정적 팩토리에서 자동으로
 * 동기화한다. 컨트롤러는 {@code ResponseEntity}로 본 객체를 감싸 동일한 상태값을 HTTP 헤더에도
 * 명시한다(예: {@code ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(...))}).
 *
 * @param <T> 응답 데이터 타입
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    /** 성공 여부 플래그. */
    private final boolean success;

    /** HTTP 상태값. 정적 팩토리에서 응답 헤더의 상태와 동기화된다. */
    private final int code;

    /** 결과 메시지(컨텍스트별 문구). */
    private final String message;

    /** 실제 응답 데이터. 실패 응답에서는 {@code null}. */
    private final T data;

    /** 실패 응답의 에러 상세. 성공 응답에서는 {@code null}. */
    private final ErrorPayload error;

    /**
     * HTTP 200(OK) 성공 응답을 생성한다.
     *
     * @param data    응답 데이터
     * @param message 결과 메시지(컨텍스트별 문구)
     * @param <T>     응답 데이터 타입
     * @return 200 응답
     */
    public static <T> ApiResponse<T> success(T data, String message) {
        return success(HttpStatus.OK, data, message);
    }

    /**
     * 지정된 HTTP 상태로 성공 응답을 생성한다.
     *
     * <p>{@code status.value()}가 본문의 {@code code} 필드로 자동 동기화되므로,
     * 같은 상태값을 두 곳에 적는 실수를 막는다.
     *
     * @param status  HTTP 상태(예: {@code HttpStatus.CREATED})
     * @param data    응답 데이터
     * @param message 결과 메시지
     * @param <T>     응답 데이터 타입
     * @return 지정된 상태의 성공 응답
     */
    public static <T> ApiResponse<T> success(HttpStatus status, T data, String message) {
        return new ApiResponse<>(true, status.value(), message, data, null);
    }

    /**
     * 도메인 에러 코드의 기본 메시지로 실패 응답을 생성한다.
     *
     * @param errorCode 도메인 에러 코드
     * @return 실패 응답
     */
    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return failure(errorCode, errorCode.getDefaultMessage(), null);
    }

    /**
     * 도메인 에러 코드와 컨텍스트 메시지로 실패 응답을 생성한다.
     *
     * @param errorCode 도메인 에러 코드
     * @param message   컨텍스트별 결과 메시지(예: 강의 ID·필드명을 포함한 구체 메시지)
     * @return 실패 응답
     */
    public static ApiResponse<Void> failure(ErrorCode errorCode, String message) {
        return failure(errorCode, message, null);
    }

    /**
     * 검증 실패용 실패 응답을 생성한다. {@code details}에 필드별 오류가 담긴다.
     *
     * @param errorCode 도메인 에러 코드(일반적으로 {@link ErrorCode#VALIDATION_FAILED})
     * @param details   필드별 검증 오류 목록
     * @return 실패 응답
     */
    public static ApiResponse<Void> failure(ErrorCode errorCode, List<FieldError> details) {
        return failure(errorCode, errorCode.getDefaultMessage(), details);
    }

    private static ApiResponse<Void> failure(ErrorCode errorCode, String message, List<FieldError> details) {
        ErrorPayload payload = new ErrorPayload(errorCode.getCode(), errorCode.name(), details);
        return new ApiResponse<>(false, errorCode.getHttpStatus().value(), message, null, payload);
    }

    /**
     * 실패 응답의 {@code error} 객체. 도메인 에러 식별 정수·식별자·필드별 상세를 담는다.
     *
     * @param code    6자리 도메인 에러 식별 정수
     * @param message 에러 식별자({@link ErrorCode}의 enum 이름)
     * @param details 검증 실패 시 필드별 오류 목록. 그 외에는 {@code null}.
     */
    public record ErrorPayload(int code, String message, List<FieldError> details) {
    }

    /**
     * 검증 실패 시 {@link ErrorPayload}의 {@code details}에 담기는 필드별 오류 항목.
     *
     * <p>Spring의 {@code @Valid} 검증 실패에서 각 필드 오류를 한 항목으로 변환해
     * 클라이언트에 전달한다.
     *
     * @param field  오류가 발생한 요청 본문 필드명
     * @param reason 오류 발생에 대한 이유
     */
    public record FieldError(String field, String reason) {
    }
}
