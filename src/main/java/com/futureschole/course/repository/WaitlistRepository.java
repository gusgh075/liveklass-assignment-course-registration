package com.futureschole.course.repository;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    /** 사용자가 해당 강의 대기열에 이미 들어가 있는지 확인한다(중복 진입 방지). */
    boolean existsByUserAndCourse(User user, Course course);

    /** 강의의 대기열 인원 수를 센다(대기 인원 산정 및 신규 진입 시 순번 계산). */
    int countByCourse(Course course);

    /**
     * 여러 강의의 대기열 인원을 강의별로 묶어 한 번에 집계한다.
     *
     * <p>강의 목록 조회에서 페이지에 담긴 강의들의 대기 인원을 N+1 없이 구하기 위한 배치 카운트다.
     * 대기자가 없는 강의는 결과 행에 포함되지 않으므로 호출 측에서 0으로 보정한다.
     *
     * @param courseIds 집계 대상 강의 식별자 목록(비어 있지 않아야 한다)
     * @return 강의별 {@code (강의 ID, 대기 인원)} 프로젝션 목록
     */
    @Query("select w.course.id as courseId, count(w) as count from Waitlist w "
            + "where w.course.id in :courseIds group by w.course.id")
    List<CourseCountProjection> countByCourseIds(@Param("courseIds") Collection<Long> courseIds);

    /**
     * 대기열에 사람이 있는 강의의 식별자를 중복 없이 조회한다(자동 승급 스캐너용).
     *
     * @return 대기 인원이 있는 강의 식별자 목록
     */
    @Query("select distinct w.course.id from Waitlist w")
    List<Long> findDistinctCourseIds();

    /**
     * 강의의 대기열을 진입 시각 오름차순으로 조회한다(자동 승급 시 헤드부터 채움).
     *
     * <p>{@code (course_id, entered_at)} 인덱스를 전제로 한다. 승급은 빈 자리 수만큼 앞에서부터 꺼내므로
     * {@link Pageable}로 필요한 개수만 가져온다.
     *
     * @param course   대상 강의
     * @param pageable 가져올 개수·정렬(진입 시각 오름차순)
     * @return 진입 시각이 이른 순의 대기열 레코드
     */
    List<Waitlist> findByCourseOrderByEnteredAtAsc(Course course, Pageable pageable);
}
