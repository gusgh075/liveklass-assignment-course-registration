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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class EnrollmentRepositoryFindByUserTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 6, 1, 10, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 7, 31, 18, 0);

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
        Enrollment enrollment = Enrollment.pending(u, course);
        ReflectionTestUtils.setField(enrollment, "status", status);
        em.persist(enrollment);
    }

    @Test
    @DisplayName("findByUser는 본인의 신청을 모든 상태(PENDING·CONFIRMED·CANCELLED) 포함해 페이지로 반환한다")
    void findByUser_includesAllStatuses() {
        persistEnrollment(user, EnrollmentStatus.PENDING);
        persistEnrollment(user, EnrollmentStatus.CONFIRMED);
        persistEnrollment(user, EnrollmentStatus.CANCELLED);
        em.flush();
        em.clear();

        Page<Enrollment> page = enrollmentRepository.findByUser(user, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent())
                .extracting(Enrollment::getStatus)
                .containsExactlyInAnyOrder(
                        EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED, EnrollmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("findByUser는 다른 사용자의 신청은 제외한다")
    void findByUser_excludesOtherUsers() {
        User other = em.persist(User.builder().userId("user-002").role(UserRole.ROLE_USER).build());
        persistEnrollment(user, EnrollmentStatus.PENDING);
        persistEnrollment(other, EnrollmentStatus.PENDING);
        persistEnrollment(other, EnrollmentStatus.CONFIRMED);
        em.flush();
        em.clear();

        Page<Enrollment> page = enrollmentRepository.findByUser(user, PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent())
                .extracting(e -> e.getUser().getUserId())
                .containsExactly("user-001");
    }

    @Test
    @DisplayName("findByUser는 연관 강의를 함께 로드해 항목이 강의 제목·가격·정원을 노출할 수 있게 한다")
    void findByUser_fetchesCourse() {
        persistEnrollment(user, EnrollmentStatus.PENDING);
        em.flush();
        em.clear();

        Page<Enrollment> page = enrollmentRepository.findByUser(user, PageRequest.of(0, 20));

        assertThat(page.getContent()).hasSize(1);
        Course loaded = page.getContent().get(0).getCourse();
        assertThat(loaded.getTitle()).isEqualTo("제목");
        assertThat(loaded.getPrice()).isEqualTo(10000);
        assertThat(loaded.getCapacity()).isEqualTo(5);
    }

    @Test
    @DisplayName("findByUser는 페이지 크기를 넘는 신청을 페이지네이션해 메타데이터를 채운다")
    void findByUser_paginates() {
        for (int i = 0; i < 3; i++) {
            persistEnrollment(user, EnrollmentStatus.PENDING);
        }
        em.flush();
        em.clear();

        Pageable pageable = PageRequest.of(0, 2);
        Page<Enrollment> page = enrollmentRepository.findByUser(user, pageable);

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);
    }
}
