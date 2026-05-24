package com.futureschole.course.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

/**
 * 강의 등록 요청 본문.
 *
 * <p>OpenAPI {@code CourseCreateRequest} 스키마와 1:1 대응한다. 컨트롤러의 {@code @Valid}
 * 검증을 통과하면 서비스로 그대로 전달되며, 검증에 실패하면 {@code GlobalExceptionHandler}가
 * {@code VALIDATION_FAILED}로 변환해 응답한다.
 *
 * @param title       강의 제목 (공백 불가)
 * @param description 강의 설명 (공백 불가)
 * @param price       강의 가격 (0 이상)
 * @param capacity    정원, 최대 수강 인원 (1 이상)
 * @param startDate   수강 기간 시작일
 * @param endDate     수강 기간 종료일
 */
public record CourseCreateRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotNull @PositiveOrZero Integer price,
        @NotNull @Min(1) Integer capacity,
        @NotNull LocalDateTime startDate,
        @NotNull LocalDateTime endDate
) {
}
