package com.futureschole.course.dto.request;

import com.futureschole.course.entity.type.CourseStatus;
import jakarta.validation.constraints.NotNull;

/**
 * 강의 상태 변경 요청 본문.
 *
 * <p>OpenAPI {@code CourseStatusChangeRequest} 스키마와 1:1 대응한다. 크리에이터가 자신의 강의를
 * 오픈({@code DRAFT → OPEN}) 또는 마감({@code OPEN → CLOSED})할 때 목표 상태를 담아 보낸다.
 * 허용되지 않는 전이는 서비스에서 거부한다.
 *
 * @param status 전이 목표 상태
 */
public record CourseStatusChangeRequest(
        @NotNull CourseStatus status
) {
}
