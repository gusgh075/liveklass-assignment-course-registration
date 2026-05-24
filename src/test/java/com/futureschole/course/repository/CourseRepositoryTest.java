package com.futureschole.course.repository;

import com.futureschole.course.config.JpaAuditingConfig;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class CourseRepositoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 18, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    @DisplayName("findByIdForUpdate는 비관적 락으로 강의를 조회한다")
    void findByIdForUpdate_returnsCourse() {
        User creator = em.persist(User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build());
        Course course = em.persist(Course.draftOf(creator, "제목", "설명", 10000, 5, START, END));
        em.flush();
        em.clear();

        Optional<Course> found = courseRepository.findByIdForUpdate(course.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(course.getId());
    }
}
