package com.futureschole.course.repository;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 사용자·강의 조합에 주어진 상태의 신청이 존재하는지 확인한다(활성 신청 중복 사전 검증용). */
    boolean existsByUserAndCourseAndStatusIn(User user, Course course, Collection<EnrollmentStatus> statuses);

    /**
     * 한 사용자의 수강 신청을 모든 상태 포함해 페이지로 조회한다(내 신청 목록 조회용).
     *
     * <p>{@code PENDING}/{@code CONFIRMED}/{@code CANCELLED}를 가리지 않고 본인 신청 전체를 반환한다.
     * 목록 항목이 강의 제목·가격·정원을 노출하므로 {@link EntityGraph}로 연관 강의를 함께 로드해
     * N+1을 피한다. {@code (user_id, course_id)} 인덱스를 전제로 한다.
     *
     * @param user     조회 대상 사용자
     * @param pageable 페이지 요청(번호·크기·정렬)
     * @return 강의가 함께 로드된 수강 신청 페이지
     */
    @EntityGraph(attributePaths = "course")
    Page<Enrollment> findByUser(User user, Pageable pageable);

    /**
     * 강의의 주어진 상태 신청을 페이지로 조회한다(강의별 수강생 목록 조회용).
     *
     * <p>목록 항목이 신청자의 외부 식별자를 노출하므로, 사용자 연관을 함께 즉시 로드해 페이지 항목마다
     * 사용자를 다시 조회하는 N+1을 피한다. 정원 산정과 동일하게 {@code PENDING}+{@code CONFIRMED}만
     * 대상으로 하며, {@code (course_id, status)} 인덱스를 전제로 한다.
     *
     * @param course   대상 강의
     * @param statuses 조회에 포함할 신청 상태
     * @param pageable 페이지·정렬 정보
     * @return 조건을 만족하는 신청 페이지
     */
    @EntityGraph(attributePaths = "user")
    Page<Enrollment> findByCourseAndStatusIn(
            Course course, Collection<EnrollmentStatus> statuses, Pageable pageable);

    /** 강의의 주어진 상태 신청 수를 센다(정원 산정은 {@code PENDING}+{@code CONFIRMED}). */
    int countByCourseAndStatusIn(Course course, Collection<EnrollmentStatus> statuses);

    /**
     * 여러 강의의 활성 신청 수를 강의별로 묶어 한 번에 집계한다.
     *
     * <p>강의 목록 조회에서 페이지에 담긴 강의들의 신청 인원을 N+1 없이 구하기 위한 배치 카운트다.
     * 정원 산정과 동일하게 {@code PENDING}+{@code CONFIRMED}만 세며, 신청이 하나도 없는 강의는
     * 결과 행에 포함되지 않으므로 호출 측에서 0으로 보정한다. {@code (course_id, status)} 인덱스를
     * 전제로 한다.
     *
     * @param courseIds 집계 대상 강의 식별자 목록(비어 있지 않아야 한다)
     * @param statuses  집계에 포함할 신청 상태
     * @return 강의별 {@code (강의 ID, 신청 수)} 프로젝션 목록
     */
    @Query("select e.course.id as courseId, count(e) as count from Enrollment e "
            + "where e.course.id in :courseIds and e.status in :statuses group by e.course.id")
    List<CourseCountProjection> countActiveByCourseIds(@Param("courseIds") Collection<Long> courseIds,
                                                       @Param("statuses") Collection<EnrollmentStatus> statuses);
}
