package com.futureschole.course.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * 대기열 진입 레코드.
 *
 * <p>정원이 찬 강의에 신청하면 거부하지 않고 대기열에 등록한다. 자리가 발생하면 진입 시각이 가장 이른
 * 헤드를 꺼내 새 {@code PENDING} 수강 신청으로 승급한다(이 레코드는 제거된다). 대기열은 신청 상태를
 * 늘리지 않기 위해 {@link Enrollment}와 분리된 별도 테이블로 둔다.
 *
 * <p>인덱스 두 종을 부여한다.
 * <ul>
 *     <li>{@code (course_id, entered_at)} — 강의별 대기열 헤드 조회(자동 승급)</li>
 *     <li>{@code (user_id, course_id)} 유니크 — 동일 사용자의 동일 강의 대기열 중복 진입 방지</li>
 * </ul>
 */
@Getter
@Entity
@Table(
        name = "waitlist",
        indexes = {
                @Index(name = "idx_waitlist_course_id_entered_at", columnList = "course_id, entered_at"),
                @Index(name = "uk_waitlist_user_id_course_id", columnList = "user_id, course_id", unique = true)
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Waitlist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** 대기열 진입 시각. 자동 승급 시 순번(가장 먼저 들어온 사용자 우선)의 기준이 된다. */
    @Column(name = "entered_at", nullable = false)
    private LocalDateTime enteredAt;

    private Waitlist(User user, Course course, LocalDateTime enteredAt) {
        this.user = user;
        this.course = course;
        this.enteredAt = enteredAt;
    }

    /**
     * 대기열 진입 레코드를 생성한다.
     *
     * <p>정원이 찬 강의에 신청이 들어왔을 때 호출되는 정적 팩토리. 진입 시각은 호출 측(서비스)이 정한
     * 값을 그대로 사용해, 순번 산정의 기준 시각을 한곳에서 관리한다.
     *
     * @param user 대기 사용자
     * @param course 대기 대상 강의
     * @param enteredAt 대기열 진입 시각
     * @return 대기열 진입 레코드
     */
    public static Waitlist enqueue(User user, Course course, LocalDateTime enteredAt) {
        return new Waitlist(user, course, enteredAt);
    }
}
