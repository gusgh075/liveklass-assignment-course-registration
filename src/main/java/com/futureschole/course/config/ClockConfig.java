package com.futureschole.course.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 시간 의존성을 주입 가능한 빈으로 노출하는 설정.
 *
 * <p>결제 기한 만료 판정처럼 "현재 시각"에 의존하는 도메인 로직이 {@link Clock}을 주입받게 해
 * 테스트에서 고정 시각({@code Clock.fixed})으로 결정적으로 검증할 수 있도록 한다. 운영에서는
 * 시스템 기본 시간대의 실제 시각을 사용한다.
 */
@Configuration
public class ClockConfig {

    /**
     * 시스템 기본 시간대를 따르는 실제 시계를 제공한다.
     *
     * @return 운영용 {@link Clock}
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
