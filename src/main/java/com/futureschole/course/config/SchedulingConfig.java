package com.futureschole.course.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화 설정.
 *
 * <p>{@code @Scheduled} 폴링 작업(결제 기한 만료 취소·종료일 자동 마감·대기열 자동 승급)을 구동한다.
 * {@link EnableScheduling}을 부트 클래스가 아닌 별도 설정으로 분리해, 스케줄링이 필요 없는 슬라이스
 * 테스트({@code @WebMvcTest}/{@code @DataJpaTest})의 컨텍스트에는 로드되지 않게 한다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
