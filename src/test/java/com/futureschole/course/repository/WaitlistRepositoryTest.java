package com.futureschole.course.repository;

import com.futureschole.course.config.JpaAuditingConfig;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
import com.futureschole.course.entity.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class WaitlistRepositoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 18, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private WaitlistRepository waitlistRepository;

    private User user;
    private Course course;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder().userId("user-001").role(UserRole.ROLE_USER).build());
        User creator = em.persist(User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build());
        course = em.persist(Course.draftOf(creator, "제목", "설명", 10000, 2, START, END));
    }

    private User persistUser(String userId) {
        return em.persist(User.builder().userId(userId).role(UserRole.ROLE_USER).build());
    }

    @Test
    @DisplayName("countByCourse는 해당 강의의 대기열 인원 수를 센다")
    void countByCourse() {
        em.persist(Waitlist.enqueue(user, course, START.plusMinutes(1)));
        em.persist(Waitlist.enqueue(persistUser("user-002"), course, START.plusMinutes(2)));
        em.flush();

        assertThat(waitlistRepository.countByCourse(course)).isEqualTo(2);
    }

    @Test
    @DisplayName("existsByUserAndCourse는 대기열 진입 여부를 반환한다")
    void existsByUserAndCourse() {
        em.persist(Waitlist.enqueue(user, course, START.plusMinutes(1)));
        User other = persistUser("user-002");
        em.flush();

        assertThat(waitlistRepository.existsByUserAndCourse(user, course)).isTrue();
        assertThat(waitlistRepository.existsByUserAndCourse(other, course)).isFalse();
    }

    @Test
    @DisplayName("countByCourseIds는 여러 강의의 대기 인원을 강의별로 묶어 집계하고 대기자 없는 강의는 제외한다")
    void countByCourseIds_groupsByCourse() {
        User creator = em.persist(User.builder().userId("creator-batch").role(UserRole.ROLE_CREATOR).build());
        Course other = em.persist(Course.draftOf(creator, "다른 강의", "설명", 10000, 2, START, END));
        Course empty = em.persist(Course.draftOf(creator, "대기 없는 강의", "설명", 10000, 2, START, END));

        em.persist(Waitlist.enqueue(user, course, START.plusMinutes(1)));
        em.persist(Waitlist.enqueue(persistUser("user-002"), course, START.plusMinutes(2)));
        em.persist(Waitlist.enqueue(persistUser("user-003"), other, START.plusMinutes(3)));
        em.flush();

        Map<Long, Long> counts = waitlistRepository
                .countByCourseIds(List.of(course.getId(), other.getId(), empty.getId())).stream()
                .collect(Collectors.toMap(CourseCountProjection::getCourseId, CourseCountProjection::getCount));

        assertThat(counts).containsEntry(course.getId(), 2L);
        assertThat(counts).containsEntry(other.getId(), 1L);
        assertThat(counts).doesNotContainKey(empty.getId());
    }
}
