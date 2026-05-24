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

    /**
     * 임시저장 상태의 강의 정보를 새 값으로 통째로 교체한다.
     *
     * <p>제목·설명·가격·정원·수강 기간을 한 번에 전체 교체하는 메서드로, {@link #draftOf} 정적 팩토리와
     * 대칭을 이룬다. 강의 상태({@code DRAFT})와 작성자, 식별자는 바꾸지 않는다.
     *
     * <p>수정 가능 여부(현재 {@code DRAFT}인지, 본인 강의인지) 판단은 서비스가 맡으므로, 이 메서드는
     * 전달받은 값을 그대로 반영하기만 한다.
     *
     * @param title 새 강의 제목
     * @param description 새 강의 설명
     * @param price 새 강의 가격
     * @param capacity 새 정원(최대 수강 인원)
     * @param startDate 새 수강 기간 시작일
     * @param endDate 새 수강 기간 종료일
     */
    public void modifyDraft(String title, String description, int price, int capacity,
                            LocalDateTime startDate, LocalDateTime endDate) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * 강의를 모집 중 상태로 전환한다.
     *
     * <p>상태를 {@link CourseStatus#OPEN}으로만 바꾼다. 현재 상태에서 오픈이 적법한지, 종료일이
     * 경과하지 않았는지 같은 판단은 서비스가 맡으므로, 이 메서드는 상태만 반영한다.
     */
    public void open() {
        this.status = CourseStatus.OPEN;
    }

    /**
     * 강의를 모집 마감 상태로 전환한다.
     *
     * <p>상태를 {@link CourseStatus#CLOSED}로만 바꾼다. 전이 적법성 판단은 서비스가 맡으므로, 이
     * 메서드는 상태만 반영한다.
     */
    public void close() {
        this.status = CourseStatus.CLOSED;
    }
}
