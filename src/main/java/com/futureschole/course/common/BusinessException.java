package com.futureschole.course.common;

import lombok.Getter;

/**
 * 도메인 비즈니스 규칙 위반을 표현하는 단일 예외.
 *
 * <p>도메인 서비스/컨트롤러는 본 예외 하나를 {@link ErrorCode}와 함께 던지면 되고,
 * {@link GlobalExceptionHandler}의 단일 핸들러가 HTTP 응답으로 변환한다. 에러 코드별로
 * 별도 예외 클래스를 만들지 않는다.
 *
 * <p>메시지 인자가 없으면 {@link ErrorCode#getDefaultMessage()}가 그대로 사용되고,
 * 컨텍스트를 끼워야 할 때는 두 번째 인자로 구체 문구를 전달한다(예: 강의 ID나 필드명을 포함한 메시지).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
