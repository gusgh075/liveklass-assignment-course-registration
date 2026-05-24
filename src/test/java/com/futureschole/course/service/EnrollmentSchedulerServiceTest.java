package com.futureschole.course.service;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.EnrollmentStatus;
import com.futureschole.course.entity.type.UserRole;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.EnrollmentRepository;
import com.futureschole.course.repository.UserRepository;
import com.futureschole.course.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnrollmentSchedulerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 24, 14, 30);
    private static final List<EnrollmentStatus> ACTIVE =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private WaitlistRepository waitlistRepository;

    private EnrollmentService enrollmentService;

    private User user;
    private Course course;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        enrollmentService = new EnrollmentService(
                userRepository, courseRepository, enrollmentRepository, waitlistRepository, clock);

        user = User.builder().userId("user-001").role(UserRole.ROLE_USER).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        User creator = User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build();
        course = Course.draftOf(creator, "제목", "설명", 10000, 2,
                NOW.minusDays(10), NOW.plusMonths(2));
        ReflectionTestUtils.setField(course, "id", 1L);
        ReflectionTestUtils.setField(course, "status", CourseStatus.OPEN);
    }

    private Enrollment pending(User u) {
        return Enrollment.pending(u, course);
    }

    @Test
    @DisplayName("expirePendingPayments는 만료 대상 PENDING을 모두 CANCELLED로 전이하고 건수를 반환한다")
    void expirePendingPayments() {
        Enrollment e1 = pending(user);
        Enrollment e2 = pending(User.builder().userId("user-002").role(UserRole.ROLE_USER).build());
        given(enrollmentRepository.findByStatusAndCreatedAtBefore(
                eq(EnrollmentStatus.PENDING), eq(NOW.minusMinutes(30))))
                .willReturn(List.of(e1, e2));

        int count = enrollmentService.expirePendingPayments();

        assertThat(count).isEqualTo(2);
        assertThat(e1.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        assertThat(e1.getCancelledAt()).isEqualTo(NOW);
        assertThat(e2.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
    }

    @Test
    @DisplayName("findWaitlistedCourseIds는 대기열 보유 강의 식별자를 그대로 반환한다")
    void findWaitlistedCourseIds() {
        given(waitlistRepository.findDistinctCourseIds()).willReturn(List.of(10L, 20L));

        assertThat(enrollmentService.findWaitlistedCourseIds()).containsExactly(10L, 20L);
    }

    @Test
    @DisplayName("promoteCourse는 빈 자리 수만큼 대기열 헤드를 PENDING으로 승급하고 대기 레코드를 제거한다")
    void promoteCourse_promotesUpToAvailableSeats() {
        // 정원 2, 활성 1 → 자리 1개
        given(courseRepository.findByIdForUpdate(1L)).willReturn(Optional.of(course));
        given(enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE)).willReturn(1);
        Waitlist head = Waitlist.enqueue(user, course, NOW.minusMinutes(5));
        given(waitlistRepository.findByCourseOrderByEnteredAtAsc(course, PageRequest.of(0, 1)))
                .willReturn(List.of(head));

        enrollmentService.promoteCourse(1L);

        verify(enrollmentRepository).save(any(Enrollment.class));
        verify(waitlistRepository).delete(head);
    }

    @Test
    @DisplayName("promoteCourse는 빈 자리가 없으면 대기열을 조회하지 않고 아무 일도 하지 않는다")
    void promoteCourse_noFreeSeats() {
        given(courseRepository.findByIdForUpdate(1L)).willReturn(Optional.of(course));
        given(enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE)).willReturn(2); // 정원=2, 만석

        enrollmentService.promoteCourse(1L);

        verify(waitlistRepository, never()).findByCourseOrderByEnteredAtAsc(any(), any());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("promoteCourse는 강의가 OPEN이 아니면 정원을 확인하지 않고 종료한다")
    void promoteCourse_courseNotOpen() {
        ReflectionTestUtils.setField(course, "status", CourseStatus.CLOSED);
        given(courseRepository.findByIdForUpdate(1L)).willReturn(Optional.of(course));

        enrollmentService.promoteCourse(1L);

        verify(enrollmentRepository, never()).countByCourseAndStatusIn(any(), any());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("promoteCourse는 강의가 없으면 아무 일도 하지 않는다")
    void promoteCourse_courseNotFound() {
        given(courseRepository.findByIdForUpdate(1L)).willReturn(Optional.empty());

        enrollmentService.promoteCourse(1L);

        verify(enrollmentRepository, never()).save(any());
    }
}
