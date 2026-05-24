package com.futureschole.course.repository;

import com.futureschole.course.config.JpaAuditingConfig;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스케줄러가 사용하는 조회 쿼리의 슬라이스 테스트.
 *
 * <p>결제 기한 만료 조회({@code findByStatusAndCreatedAtBefore})는 {@code createdAt}이 감사 관리되는
 * {@code updatable=false} 컬럼이라 테스트에서 임의 시각으로 제어하기 어렵고, Spring Data 파생 쿼리의
 * 표준 동작이므로 여기서는 다루지 않는다(만료 전이 로직은 서비스 단위 테스트가 검증한다).
 */
@DataJpaTest
@Import(JpaAuditingConfig.class)
class SchedulerRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 24, 14, 30);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    private User creator;
    private User user;

    @BeforeEach
    void setUp() {
        creator = em.persist(User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build());
        user = em.persist(User.builder().userId("user-001").role(UserRole.ROLE_USER).build());
    }

    private Course persistCourse(CourseStatus status, LocalDateTime endDate) {
        Course course = Course.draftOf(creator, "제목", "설명", 10000, 5,
                LocalDateTime.of(2026, 6, 1, 10, 0), endDate);
        ReflectionTestUtils.setField(course, "status", status);
        return em.persist(course);
    }

    private User persistUser(String userId) {
        return em.persist(User.builder().userId(userId).role(UserRole.ROLE_USER).build());
    }

    @Test
    @DisplayName("findByStatusAndEndDateBefore는 종료일이 기준 시각 이전인 OPEN 강의만 반환한다")
    void findByStatusAndEndDateBefore() {
        Course endedOpen = persistCourse(CourseStatus.OPEN, NOW.minusDays(1));    // 마감 대상
        persistCourse(CourseStatus.OPEN, NOW.plusDays(1));                        // 종료 전
        persistCourse(CourseStatus.CLOSED, NOW.minusDays(1));                     // 상태 제외
        em.flush();

        List<Course> ended = courseRepository.findByStatusAndEndDateBefore(CourseStatus.OPEN, NOW);

        assertThat(ended).extracting(Course::getId).containsExactly(endedOpen.getId());
    }

    @Test
    @DisplayName("findDistinctCourseIds는 대기열이 있는 강의 식별자를 중복 없이 반환한다")
    void findDistinctCourseIds() {
        Course courseA = persistCourse(CourseStatus.OPEN, NOW.plusMonths(2));
        Course courseB = persistCourse(CourseStatus.OPEN, NOW.plusMonths(2));
        em.persist(Waitlist.enqueue(user, courseA, NOW.minusMinutes(5)));
        em.persist(Waitlist.enqueue(persistUser("user-002"), courseA, NOW.minusMinutes(3)));
        em.persist(Waitlist.enqueue(persistUser("user-003"), courseB, NOW.minusMinutes(1)));
        em.flush();

        List<Long> ids = waitlistRepository.findDistinctCourseIds();

        assertThat(ids).containsExactlyInAnyOrder(courseA.getId(), courseB.getId());
    }

    @Test
    @DisplayName("findByCourseOrderByEnteredAtAsc는 진입 시각이 이른 순으로 요청한 개수만 반환한다")
    void findByCourseOrderByEnteredAtAsc() {
        Course course = persistCourse(CourseStatus.OPEN, NOW.plusMonths(2));
        em.persist(Waitlist.enqueue(persistUser("late"), course, NOW.minusMinutes(1)));
        em.persist(Waitlist.enqueue(persistUser("early"), course, NOW.minusMinutes(9)));
        em.persist(Waitlist.enqueue(persistUser("mid"), course, NOW.minusMinutes(5)));
        em.flush();

        List<Waitlist> heads = waitlistRepository.findByCourseOrderByEnteredAtAsc(course, PageRequest.of(0, 2));

        assertThat(heads).extracting(w -> w.getUser().getUserId()).containsExactly("early", "mid");
    }
}
