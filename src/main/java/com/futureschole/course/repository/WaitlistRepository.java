package com.futureschole.course.repository;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
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
}
