package com.futureschole.course.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정.
 *
 * <p>{@code BaseTimeEntity}의 {@code @CreatedDate}/{@code @LastModifiedDate}가 영속/병합 시점에
 * 자동으로 채워지도록 활성화한다. 별도 {@code @Configuration}으로 분리한 이유는
 * {@code @WebMvcTest}/{@code @DataJpaTest} 같은 슬라이스 테스트가 본 설정을 손쉽게 제외할 수 있게
 * 하기 위함이다. {@code @SpringBootApplication}에 직접 부착하면 슬라이스 테스트가 컨텍스트 로딩
 * 단계에서 {@code JpaMetamodelMappingContext}를 요구해 실패한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
