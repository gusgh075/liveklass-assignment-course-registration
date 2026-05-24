package com.futureschole.course.entity;

import com.futureschole.course.entity.type.CourseStatus;
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
 * 강의(수강 신청 대상).
 *
 * <p>크리에이터가 임시저장하면 {@link CourseStatus#DRAFT}로 생성되고, 별도 상태 변경 API로
 * {@link CourseStatus#OPEN}/{@link CourseStatus#CLOSED}로 전이한다.
 *
 * <p>인덱스 두 종을 부여한다.
 * <ul>
 *     <li>{@code (status, end_date)} — 강의 목록 필터와 종료일 자동 마감 스캐너</li>
 *     <li>{@code (creator_id)} — 강사 소유권 확인 및 강의별 수강생 목록</li>
 * </ul>
 */
@Getter
@Entity
@Table(
        name = "course",
        indexes = {
                @Index(name = "idx_course_status_end_date", columnList = "status, end_date"),
                @Index(name = "idx_course_creator_id", columnList = "creator_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Course extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "start_date", nullable = false)
    private LocalDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CourseStatus status;

    private Course(User creator, String title, String description, int price, int capacity,
                   LocalDateTime startDate, LocalDateTime endDate, CourseStatus status) {
        this.creator = creator;
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
    }

    /**
     * 새 강의를 임시저장 상태로 생성한다.
     *
     * <p>강의 등록 시점에 호출되는 정적 팩토리. 생성 시점에는 항상 {@link CourseStatus#DRAFT}로
     * 시작하고, {@code OPEN}/{@code CLOSED}로의 전이는 별도 상태 변경 API에서 처리한다.
     *
     * @param creator 강의 작성자({@code ROLE_CREATOR})
     * @param title 강의 제목
     * @param description 강의 설명
     * @param price 강의 가격
     * @param capacity 정원(최대 수강 인원)
     * @param startDate 수강 기간 시작일
     * @param endDate 수강 기간 종료일
     * @return {@code DRAFT} 상태로 초기화된 강의 인스턴스
     */
    public static Course draftOf(User creator, String title, String description, int price, int capacity,
                                 LocalDateTime startDate, LocalDateTime endDate) {
        return new Course(creator, title, description, price, capacity, startDate, endDate, CourseStatus.DRAFT);
    }
}
