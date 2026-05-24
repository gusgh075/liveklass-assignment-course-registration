package com.futureschole.course.scheduler;

import com.futureschole.course.service.CourseService;
import com.futureschole.course.service.EnrollmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ScheduledTasksTest {

    @Mock
    private CourseService courseService;
    @Mock
    private EnrollmentService enrollmentService;

    @InjectMocks
    private ScheduledTasks scheduledTasks;

    @Test
    @DisplayName("expirePendingPayments는 결제 만료 취소를 서비스에 위임한다")
    void expirePendingPayments_delegates() {
        scheduledTasks.expirePendingPayments();

        verify(enrollmentService).expirePendingPayments();
    }

    @Test
    @DisplayName("closeEndedCourses는 종료일 마감을 서비스에 위임한다")
    void closeEndedCourses_delegates() {
        scheduledTasks.closeEndedCourses();

        verify(courseService).closeEndedCourses();
    }

    @Test
    @DisplayName("promoteWaitlists는 대기열 보유 강의마다 승급을 호출한다")
    void promoteWaitlists_iteratesEachCourse() {
        given(enrollmentService.findWaitlistedCourseIds()).willReturn(List.of(10L, 20L));

        scheduledTasks.promoteWaitlists();

        verify(enrollmentService).promoteCourse(10L);
        verify(enrollmentService).promoteCourse(20L);
    }
}
