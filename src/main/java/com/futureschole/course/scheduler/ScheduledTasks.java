package com.futureschole.course.scheduler;

import com.futureschole.course.service.CourseService;
import com.futureschole.course.service.EnrollmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 시간 기반 도메인 작업을 분 단위로 폴링하는 스케줄러.
 *
 * <p>결제 기한 만료 취소·종료일 자동 마감·대기열 자동 승급을 각각 주기적으로 수행한다. 실제 도메인 처리는
 * 서비스에 위임하고, 본 클래스는 트리거와 실행 로그만 담당한다. 스케줄링 활성화는 {@code SchedulingConfig}가
 * 맡는다.
 *
 * <p>대기열 승급은 강의별로 별도 트랜잭션·비관적 락으로 처리해야 하므로, 대상 강의 식별자를 먼저 모은 뒤
 * 강의마다 서비스를 호출한다(프록시 경계를 넘어 강의 단위 트랜잭션이 열리도록).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    /** 폴링 주기(밀리초). 직전 실행이 끝난 뒤 이 간격을 두고 다시 실행한다. */
    private static final long POLL_INTERVAL_MS = 60_000L;

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    /** 결제 기한이 지난 {@code PENDING} 신청을 일괄 취소한다. */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void expirePendingPayments() {
        int expired = enrollmentService.expirePendingPayments();
        if (expired > 0) {
            log.info("결제 기한 만료로 취소된 신청 수: {}", expired);
        }
    }

    /** 종료일이 지난 {@code OPEN} 강의를 일괄 마감한다. */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void closeEndedCourses() {
        int closed = courseService.closeEndedCourses();
        if (closed > 0) {
            log.info("종료일 경과로 마감된 강의 수: {}", closed);
        }
    }

    /** 자리가 빈 강의의 대기열 헤드를 결제 대기 신청으로 승급한다. */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    public void promoteWaitlists() {
        List<Long> courseIds = enrollmentService.findWaitlistedCourseIds();
        courseIds.forEach(enrollmentService::promoteCourse);
    }
}
