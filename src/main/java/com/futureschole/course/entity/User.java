package com.futureschole.course.entity;

import com.futureschole.course.entity.type.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 시스템 사용자.
 *
 * <p>인증 인프라 미도입 단계에서는 외부 식별자 {@code userId}가 {@code X-User-Id} 헤더로
 * 전달되며, 내부 PK {@code id}와 분리되어 운영된다.
 */
@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 외부 API 식별자(비즈니스 키). {@code X-User-Id} 헤더 값과 매칭된다. */
    @Column(nullable = false, unique = true, length = 64)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserRole role;

    @Builder
    private User(String userId, UserRole role) {
        this.userId = userId;
        this.role = role;
    }
}
