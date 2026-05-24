package com.futureschole.course.repository;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.type.CourseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * 강의를 비관적 쓰기 락으로 조회한다.
     *
     * <p>수강 신청 시 정원 카운트 갱신을 직렬화하기 위해 강의 레코드에 락을 건다. 동일 강의의 마지막
     * 자리에 여러 사용자가 동시에 신청해도, 락을 잡은 트랜잭션이 끝날 때까지 다른 트랜잭션이 대기하므로
     * 정원 초과를 막는다.
     *
     * @param id 강의 식별자
     * @return 락을 획득한 강의(없으면 빈 값)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Course c where c.id = :id")
    Optional<Course> findByIdForUpdate(@Param("id") Long id);

    /**
     * 주어진 상태에 속하는 강의를 페이지 단위로 조회한다.
     *
     * <p>강의 목록 조회의 상태 필터에 사용한다. {@code (status, end_date)} 복합 인덱스를 전제로 한
     * 파생 쿼리이며, 정렬·페이지네이션은 {@link Pageable}이 담당한다.
     *
     * @param statuses 조회 대상 상태 집합(목록 조회 기본은 {@code OPEN}+{@code CLOSED})
     * @param pageable 페이지·정렬 정보
     * @return 조건을 만족하는 강의 페이지
     */
    Page<Course> findByStatusIn(Collection<CourseStatus> statuses, Pageable pageable);

    /**
     * 종료일이 지난 특정 상태의 강의를 모두 조회한다(종료일 자동 마감 스캐너용).
     *
     * <p>스케줄러가 {@code OPEN} 중 종료일이 기준 시각보다 이른 강의를 찾아 일괄 마감한다.
     * {@code (status, end_date)} 인덱스를 전제로 한다.
     *
     * @param status    조회 대상 상태({@code OPEN})
     * @param threshold 기준 시각(현재 시각). 종료일이 이보다 이른 강의가 마감 대상이다.
     * @return 마감 대상 강의 목록
     */
    List<Course> findByStatusAndEndDateBefore(CourseStatus status, LocalDateTime threshold);
}
