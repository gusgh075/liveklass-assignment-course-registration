package com.futureschole.course.repository;

import com.futureschole.course.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 외부 식별자({@code X-User-Id} 헤더 값)로 사용자를 조회한다. */
    Optional<User> findByUserId(String userId);
}
