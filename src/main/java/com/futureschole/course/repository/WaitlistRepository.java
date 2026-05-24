package com.futureschole.course.repository;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<Waitlist, Long> {

    /** 사용자가 해당 강의 대기열에 이미 들어가 있는지 확인한다(중복 진입 방지). */
    boolean existsByUserAndCourse(User user, Course course);

    /** 강의의 대기열 인원 수를 센다(대기 인원 산정 및 신규 진입 시 순번 계산). */
    int countByCourse(Course course);
}
