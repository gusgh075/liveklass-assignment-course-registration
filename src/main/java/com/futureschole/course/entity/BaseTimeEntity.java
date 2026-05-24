package com.futureschole.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 생성·수정 시각을 자동 관리하는 공통 추상 엔티티.
 *
 * <p>모든 도메인 엔티티는 본 클래스를 상속해 {@code created_at}/{@code updated_at} 컬럼을
 * 공유한다. {@link AuditingEntityListener}가 {@code @CreatedDate}/{@code @LastModifiedDate}
 * 필드를 영속/병합 시점에 채워주며, 활성화는 {@code JpaAuditingConfig}에서 담당한다.
 *
 * <p>단순 감사용을 넘어서 결제 기한 만료 스캐너가 {@code Enrollment.createdAt + 30분} 기준으로
 * 만료 대상을 잡는 등 비즈니스 로직의 시간 기준점으로도 사용된다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
