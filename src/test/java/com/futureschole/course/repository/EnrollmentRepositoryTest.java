package com.futureschole.course.repository;

import com.futureschole.course.config.JpaAuditingConfig;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.EnrollmentStatus;
import com.futureschole.course.entity.type.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class EnrollmentRepositoryTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 18, 0);
    private static final List<EnrollmentStatus> ACTIVE = List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    private User user;
    private Course course;

    @BeforeEach
    void setUp() {
        user = em.persist(User.builder().userId("user-001").role(UserRole.ROLE_USER).build());
        User creator = em.persist(User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build());
        course = em.persist(Course.draftOf(creator, "제목", "설명", 10000, 5, START, END));
    }

    private void persistEnrollment(User u, EnrollmentStatus status) {
        persistEnrollment(u, course, status);
    }

    private void persistEnrollment(User u, Course c, EnrollmentStatus status) {
        Enrollment enrollment = Enrollment.pending(u, c);
        ReflectionTestUtils.setField(enrollment, "status", status);
        em.persist(enrollment);
    }

    private User persistUser(String userId) {
        return em.persist(User.builder().userId(userId).role(UserRole.ROLE_USER).build());
    }

    @Test
    @DisplayName("countByCourseAndStatusIn은 PENDING·CONFIRMED만 세고 CANCELLED는 제외한다")
    void countByCourseAndStatusIn_excludesCancelled() {
        persistEnrollment(user, EnrollmentStatus.PENDING);
        persistEnrollment(persistUser("user-002"), EnrollmentStatus.CONFIRMED);
        persistEnrollment(persistUser("user-003"), EnrollmentStatus.CANCELLED);
        em.flush();

        int count = enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("existsByUserAndCourseAndStatusIn은 활성 신청이 있으면 true, CANCELLED만 있으면 false")
    void existsByUserAndCourseAndStatusIn() {
        persistEnrollment(user, EnrollmentStatus.PENDING);
        User cancelledUser = persistUser("user-cancel");
        persistEnrollment(cancelledUser, EnrollmentStatus.CANCELLED);
        em.flush();

        assertThat(enrollmentRepository.existsByUserAndCourseAndStatusIn(user, course, ACTIVE)).isTrue();
        assertThat(enrollmentRepository.existsByUserAndCourseAndStatusIn(cancelledUser, course, ACTIVE)).isFalse();
    }

    @Test
    @DisplayName("countActiveByCourseIds는 여러 강의의 활성 신청 수를 강의별로 묶어 집계하고 CANCELLED는 제외한다")
    void countActiveByCourseIds_groupsByCourseExcludingCancelled() {
        User creator = em.persist(User.builder().userId("creator-batch").role(UserRole.ROLE_CREATOR).build());
        Course other = em.persist(Course.draftOf(creator, "다른 강의", "설명", 10000, 5, START, END));

        persistEnrollment(user, course, EnrollmentStatus.PENDING);
        persistEnrollment(persistUser("user-002"), course, EnrollmentStatus.CONFIRMED);
        persistEnrollment(persistUser("user-003"), course, EnrollmentStatus.CANCELLED);
        persistEnrollment(persistUser("user-004"), other, EnrollmentStatus.CONFIRMED);
        em.flush();

        Map<Long, Long> counts = enrollmentRepository
                .countActiveByCourseIds(List.of(course.getId(), other.getId()), ACTIVE).stream()
                .collect(Collectors.toMap(CourseCountProjection::getCourseId, CourseCountProjection::getCount));

        assertThat(counts).containsEntry(course.getId(), 2L);
        assertThat(counts).containsEntry(other.getId(), 1L);
    }

    @Test
    @DisplayName("countActiveByCourseIds는 매칭 행이 없는 강의는 결과에 포함하지 않는다")
    void countActiveByCourseIds_omitsCoursesWithoutActiveEnrollments() {
        User creator = em.persist(User.builder().userId("creator-empty").role(UserRole.ROLE_CREATOR).build());
        Course empty = em.persist(Course.draftOf(creator, "신청 없는 강의", "설명", 10000, 5, START, END));
        persistEnrollment(user, course, EnrollmentStatus.PENDING);
        em.flush();

        Map<Long, Long> counts = enrollmentRepository
                .countActiveByCourseIds(List.of(course.getId(), empty.getId()), ACTIVE).stream()
                .collect(Collectors.toMap(CourseCountProjection::getCourseId, CourseCountProjection::getCount));

        assertThat(counts).containsEntry(course.getId(), 1L);
        assertThat(counts).doesNotContainKey(empty.getId());
    }
}
