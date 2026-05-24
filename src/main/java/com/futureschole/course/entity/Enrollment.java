package com.futureschole.course.entity;

import com.futureschole.course.entity.type.EnrollmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 수강 신청.
 *
 * <p>사용자가 강의를 신청하면 {@link EnrollmentStatus#PENDING}으로 생성되고, 결제 확정·취소에 따라
 * {@code CONFIRMED}/{@code CANCELLED}로 전이한다. 정원 산정에는 {@code PENDING}과 {@code CONFIRMED}만
 * 포함한다.
 *
 * <p>인덱스 세 종을 부여한다.
 * <ul>
 *     <li>{@code (user_id, course_id)} — 활성 신청 중복 사전 검증 및 내 신청 목록 조회. 취소 후 재신청을
 *         허용하기 위해 유니크 제약은 두지 않는다.</li>
 *     <li>{@code (course_id, status)} — 강의별 정원 산정과 강의별 수강생 목록 조회</li>
 *     <li>{@code (status, created_at)} — 결제 기한 만료 스캐너</li>
 * </ul>
 */
@Getter
@Entity
@Table(
        name = "enrollment",
        indexes = {
                @Index(name = "idx_enrollment_user_id_course_id", columnList = "user_id, course_id"),
                @Index(name = "idx_enrollment_course_id_status", columnList = "course_id, status"),
                @Index(name = "idx_enrollment_status_created_at", columnList = "status, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private EnrollmentStatus status;

    /** 결제 확정 시각. {@code PENDING → CONFIRMED} 전이 시점에 기록되며 7일 환불 기준의 기준 시각이다. */
    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    /** 취소 시각. {@code CANCELLED} 전이 시점에 기록된다. */
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    private Enrollment(User user, Course course, EnrollmentStatus status) {
        this.user = user;
        this.course = course;
        this.status = status;
    }

    /**
     * 결제 대기 상태의 새 수강 신청을 생성한다.
     *
     * <p>수강 신청 시점에 호출되는 정적 팩토리. 생성 직후에는 항상 {@link EnrollmentStatus#PENDING}이며
     * 결제 확정·취소 시각은 비어 있다. 결제 기한은 생성 시각으로부터 30분이다.
     *
     * @param user 신청 사용자
     * @param course 신청 대상 강의
     * @return {@code PENDING} 상태로 초기화된 수강 신청
     */
    public static Enrollment pending(User user, Course course) {
        return new Enrollment(user, course, EnrollmentStatus.PENDING);
    }
}
