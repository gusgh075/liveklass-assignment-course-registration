package com.futureschole.course.repository;

import com.futureschole.course.entity.Course;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
