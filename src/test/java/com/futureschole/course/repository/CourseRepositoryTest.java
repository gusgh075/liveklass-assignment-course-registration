package com.futureschole.course.repository;

import com.futureschole.course.config.JpaAuditingConfig;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
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

    @Test
    @DisplayName("findByStatusIn은 주어진 상태의 강의만 페이지로 조회하고 그 외 상태는 제외한다")
    void findByStatusIn_filtersByStatus() {
        User creator = em.persist(User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build());
        persistCourseWithStatus(creator, "DRAFT 강의", CourseStatus.DRAFT);
        Course open = persistCourseWithStatus(creator, "OPEN 강의", CourseStatus.OPEN);
        Course closed = persistCourseWithStatus(creator, "CLOSED 강의", CourseStatus.CLOSED);
        em.flush();
        em.clear();

        Page<Course> page = courseRepository.findByStatusIn(
                List.of(CourseStatus.OPEN, CourseStatus.CLOSED), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Course::getId)
                .containsExactlyInAnyOrder(open.getId(), closed.getId());
    }

    private Course persistCourseWithStatus(User creator, String title, CourseStatus status) {
        Course course = Course.draftOf(creator, title, "설명", 10000, 5, START, END);
        ReflectionTestUtils.setField(course, "status", status);
        return em.persist(course);
    }
}
