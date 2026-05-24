package com.futureschole.course.service;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.CourseStatus;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CourseSchedulerServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 24, 14, 30);

    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private EnrollmentRepository enrollmentRepository;
    @Mock
    private WaitlistRepository waitlistRepository;

    private CourseService courseService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        courseService = new CourseService(
                userRepository, courseRepository, enrollmentRepository, waitlistRepository, clock);
    }

    private Course openCourse(LocalDateTime endDate) {
        User creator = User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build();
        Course course = Course.draftOf(creator, "제목", "설명", 10000, 30, NOW.minusDays(30), endDate);
        ReflectionTestUtils.setField(course, "status", CourseStatus.OPEN);
        return course;
    }

    @Test
    @DisplayName("closeEndedCourses는 종료일이 지난 OPEN 강의를 모두 CLOSED로 전이하고 건수를 반환한다")
    void closeEndedCourses() {
        Course c1 = openCourse(NOW.minusDays(1));
        Course c2 = openCourse(NOW.minusHours(1));
        given(courseRepository.findByStatusAndEndDateBefore(eq(CourseStatus.OPEN), eq(NOW)))
                .willReturn(List.of(c1, c2));

        int count = courseService.closeEndedCourses();

        assertThat(count).isEqualTo(2);
        assertThat(c1.getStatus()).isEqualTo(CourseStatus.CLOSED);
        assertThat(c2.getStatus()).isEqualTo(CourseStatus.CLOSED);
    }
}
