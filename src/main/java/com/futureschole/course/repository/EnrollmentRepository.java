package com.futureschole.course.repository;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 사용자·강의 조합에 주어진 상태의 신청이 존재하는지 확인한다(활성 신청 중복 사전 검증용). */
    boolean existsByUserAndCourseAndStatusIn(User user, Course course, Collection<EnrollmentStatus> statuses);

    /** 강의의 주어진 상태 신청 수를 센다(정원 산정은 {@code PENDING}+{@code CONFIRMED}). */
    int countByCourseAndStatusIn(Course course, Collection<EnrollmentStatus> statuses);
}
