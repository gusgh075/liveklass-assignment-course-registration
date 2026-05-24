package com.futureschole.course.common;

import com.futureschole.course.common.ApiResponse.FieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * 컨트롤러에서 발생한 예외를 {@link ApiResponse} 표준 형식으로 변환하는 전역 핸들러.
 *
 * <p>Spring이 표준으로 던지는 검증·파싱·헤더 예외를 도메인 {@link ErrorCode}로 매핑한다.
 * 도메인별 비즈니스 예외(예: 강의 상태 불일치, 결제 기한 만료)는 해당 도메인을 구현하는 이슈에서
 * 본 핸들러에 매핑을 추가한다.
 *
 * <p>모든 응답은 {@code ResponseEntity.status(errorCode.getHttpStatus()).body(ApiResponse.failure(...))}
 * 형태로 일관되게 반환되어 HTTP 상태값과 본문의 {@code code}가 어긋나지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * {@code @Valid} 검증 실패를 처리한다. 모든 필드 오류를 모아 {@code error.details} 배열에 담는다.
     *
     * @param ex 발생한 예외
     * @return {@code 400 / VALIDATION_FAILED(140001)} 응답
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiResponse<Void> body = ApiResponse.failure(ErrorCode.VALIDATION_FAILED, details);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getHttpStatus()).body(body);
    }

    /**
     * 요청 본문이 잘못된 JSON 형식이라 파싱에 실패한 경우를 처리한다.
     *
     * @param ex 발생한 예외
     * @return {@code 400 / MALFORMED_JSON(140002)} 응답
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMalformedJson(HttpMessageNotReadableException ex) {
        return build(ErrorCode.MALFORMED_JSON);
    }

    /**
     * 필수 요청 헤더 누락을 처리한다.
     *
     * <p>{@code X-User-Id} 또는 {@code X-User-Role}이 빠진 경우는 사용자 식별 실패로 보고
     * {@link ErrorCode#USER_NOT_FOUND}를 반환하며, 그 외 헤더 누락은 일반 검증 실패로 처리한다.
     *
     * @param ex 발생한 예외
     * @return 누락된 헤더에 따른 실패 응답
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException ex) {
        String headerName = ex.getHeaderName();
        if ("X-User-Id".equalsIgnoreCase(headerName) || "X-User-Role".equalsIgnoreCase(headerName)) {
            return build(ErrorCode.USER_NOT_FOUND);
        }
        return build(ErrorCode.VALIDATION_FAILED);
    }

    /**
     * 파라미터 타입 변환 실패를 처리한다(예: {@code X-User-Role} 헤더의 enum 변환 실패,
     * 경로 변수의 숫자 변환 실패 등).
     *
     * @param ex 발생한 예외
     * @return {@code 400 / VALIDATION_FAILED(140001)} 응답
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(ErrorCode.VALIDATION_FAILED);
    }

    /**
     * 도메인 비즈니스 규칙 위반({@link BusinessException})을 처리한다.
     *
     * <p>예외에 담긴 {@link ErrorCode}의 HTTP 상태와 6자리 도메인 코드를 그대로 응답에 반영하고,
     * 본문 메시지는 예외에 실린 메시지(컨텍스트 문구 또는 기본 메시지)를 사용한다.
     *
     * @param ex 발생한 비즈니스 예외
     * @return {@link ErrorCode}에 대응하는 실패 응답
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ApiResponse<Void> body = ApiResponse.failure(errorCode, ex.getMessage());
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }

    /**
     * 위에서 잡지 못한 모든 예외에 대한 fallback. 서버 측 결함으로 간주하고 로그를 남긴다.
     *
     * @param ex 발생한 예외
     * @return {@code 500 / INTERNAL_SERVER_ERROR(150001)} 응답
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(ErrorCode.INTERNAL_SERVER_ERROR);
    }

    /**
     * 단일 에러 코드를 기본 메시지로 응답하는 공통 빌더.
     *
     * @param errorCode 도메인 에러 코드
     * @return 해당 코드의 HTTP 상태와 기본 메시지로 채워진 실패 응답
     */
    private ResponseEntity<ApiResponse<Void>> build(ErrorCode errorCode) {
        ApiResponse<Void> body = ApiResponse.failure(errorCode);
        return ResponseEntity.status(errorCode.getHttpStatus()).body(body);
    }
}