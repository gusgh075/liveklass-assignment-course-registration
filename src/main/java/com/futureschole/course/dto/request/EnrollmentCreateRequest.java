package com.futureschole.course.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 수강 신청 요청 본문.
 *
 * <p>OpenAPI {@code EnrollmentCreateRequest} 스키마와 1:1 대응한다. 신청 사용자는 본문이 아닌
 * {@code X-User-Id} 헤더로 식별하므로, 본문에는 신청 대상 강의 식별자만 담는다.
 *
 * @param courseId 신청 대상 강의 식별자
 */
public record EnrollmentCreateRequest(
        @NotNull Long courseId
) {
}
