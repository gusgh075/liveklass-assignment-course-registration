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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    @Test
    @DisplayName("findByCourseAndStatusIn은 해당 강의의 PENDING·CONFIRMED만 페이지로 반환하고 CANCELLED는 제외한다")
    void findByCourseAndStatusIn_returnsActiveOnly() {
        persistEnrollment(user, EnrollmentStatus.PENDING);
        persistEnrollment(persistUser("user-002"), EnrollmentStatus.CONFIRMED);
        persistEnrollment(persistUser("user-003"), EnrollmentStatus.CANCELLED);
        em.flush();
        em.clear();

        Page<Enrollment> page = enrollmentRepository.findByCourseAndStatusIn(course, ACTIVE, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent())
                .extracting(Enrollment::getStatus)
                .containsExactlyInAnyOrder(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("findByCourseAndStatusIn은 다른 강의의 신청은 포함하지 않는다")
    void findByCourseAndStatusIn_scopedToCourse() {
        User creator = em.persist(User.builder().userId("creator-other").role(UserRole.ROLE_CREATOR).build());
        Course other = em.persist(Course.draftOf(creator, "다른 강의", "설명", 10000, 5, START, END));
        persistEnrollment(user, course, EnrollmentStatus.PENDING);
        persistEnrollment(persistUser("user-002"), other, EnrollmentStatus.CONFIRMED);
        em.flush();
        em.clear();

        Page<Enrollment> page = enrollmentRepository.findByCourseAndStatusIn(course, ACTIVE, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getUser().getUserId()).isEqualTo("user-001");
    }

    @Test
    @DisplayName("findByCourseAndStatusIn은 EntityGraph로 user를 함께 로드해 userId 접근 시 추가 조회가 없다")
    void findByCourseAndStatusIn_fetchesUser() {
        persistEnrollment(user, EnrollmentStatus.PENDING);
        em.flush();
        em.clear();

        Page<Enrollment> page = enrollmentRepository.findByCourseAndStatusIn(course, ACTIVE, PageRequest.of(0, 20));

        Enrollment loaded = page.getContent().get(0);
        // user 연관이 EntityGraph로 즉시 로드되어 영속성 컨텍스트 초기화 후에도 식별자 접근이 가능하다.
        assertThat(loaded.getUser().getUserId()).isEqualTo("user-001");
    }

    @Test
    @DisplayName("findByCourseAndStatusIn은 페이지 크기·번호 메타데이터를 그대로 반영한다")
    void findByCourseAndStatusIn_paging() {
        for (int i = 0; i < 3; i++) {
            persistEnrollment(persistUser("user-page-" + i), EnrollmentStatus.PENDING);
        }
        em.flush();

        Pageable pageable = PageRequest.of(0, 2);
        Page<Enrollment> page = enrollmentRepository.findByCourseAndStatusIn(course, ACTIVE, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(2);
        assertThat(page.getNumber()).isZero();
        assertThat(page.getContent()).hasSize(2);
    }
}
