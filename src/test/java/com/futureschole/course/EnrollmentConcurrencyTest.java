package com.futureschole.course;

import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.EnrollmentResultType;
import com.futureschole.course.entity.type.EnrollmentStatus;
import com.futureschole.course.entity.type.UserRole;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.EnrollmentRepository;
import com.futureschole.course.repository.UserRepository;
import com.futureschole.course.repository.WaitlistRepository;
import com.futureschole.course.scheduler.ScheduledTasks;
import com.futureschole.course.service.EnrollmentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수강 신청 동시성 통합 테스트.
 *
 * <p>마지막 자리에 여러 사용자가 동시에 신청할 때 강의 레코드 비관적 락이 트랜잭션을 직렬화해 정원이
 * 초과되지 않는지 실제 멀티스레드로 검증한다. 락 경합은 실제 트랜잭션·DB가 있어야 재현되므로
 * {@code @SpringBootTest}로 전체 컨텍스트를 띄운다.
 *
 * <p>스케줄러 폴링이 끼어들지 않도록 {@link ScheduledTasks}를 목으로 대체하고, 데이터 커밋이 필요하므로
 * 테스트 메서드는 트랜잭션으로 감싸지 않고 전후로 직접 정리한다.
 */
@SpringBootTest
class EnrollmentConcurrencyTest {

    private static final int CAPACITY = 3;
    private static final int CONTENDERS = 10;
    private static final List<EnrollmentStatus> ACTIVE =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    @MockitoBean
    private ScheduledTasks scheduledTasks;

    @Autowired
    private EnrollmentService enrollmentService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private EnrollmentRepository enrollmentRepository;
    @Autowired
    private WaitlistRepository waitlistRepository;

    private Long courseId;

    @BeforeEach
    void setUp() {
        clearAll();

        User creator = userRepository.save(
                User.builder().userId("creator-001").role(UserRole.ROLE_CREATOR).build());
        Course course = Course.draftOf(creator, "동시성 강의", "설명", 10000, CAPACITY,
                LocalDateTime.now().plusDays(1), LocalDateTime.now().plusMonths(2));
        ReflectionTestUtils.setField(course, "status", CourseStatus.OPEN);
        courseId = courseRepository.save(course).getId();

        for (int i = 0; i < CONTENDERS; i++) {
            userRepository.save(User.builder().userId("user-" + i).role(UserRole.ROLE_USER).build());
        }
    }

    @AfterEach
    void tearDown() {
        clearAll();
    }

    private void clearAll() {
        waitlistRepository.deleteAll();
        enrollmentRepository.deleteAll();
        courseRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("정원 3 강의에 10명이 동시에 신청해도 정확히 3명만 신청되고 나머지는 대기열에 들어간다")
    void concurrentApply_doesNotExceedCapacity() throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        CountDownLatch ready = new CountDownLatch(CONTENDERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(CONTENDERS);
        List<EnrollmentResultType> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < CONTENDERS; i++) {
            String userId = "user-" + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    EnrollmentCreateResponse response =
                            enrollmentService.apply(userId, new EnrollmentCreateRequest(courseId));
                    results.add(response.resultType());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();          // 모든 스레드가 출발선에 설 때까지 대기
        start.countDown();      // 동시에 출발
        boolean finished = done.await(20, TimeUnit.SECONDS);
        pool.shutdownNow();

        assertThat(finished).as("모든 신청이 제한 시간 안에 끝나야 한다").isTrue();
        assertThat(errors).as("동시 신청 중 예외가 없어야 한다").isEmpty();

        long enrolled = results.stream().filter(r -> r == EnrollmentResultType.ENROLLED).count();
        long waitlisted = results.stream().filter(r -> r == EnrollmentResultType.WAITLISTED).count();
        assertThat(enrolled).as("정원만큼만 신청 확정").isEqualTo(CAPACITY);
        assertThat(waitlisted).as("나머지는 대기열 진입").isEqualTo(CONTENDERS - CAPACITY);

        // DB 불변식: 활성 신청 수가 정원을 넘지 않는다.
        Course course = courseRepository.findById(courseId).orElseThrow();
        assertThat(enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE))
                .as("활성 신청 수는 정원과 같아야 한다(초과 없음)")
                .isEqualTo(CAPACITY);
        assertThat(waitlistRepository.countByCourse(course))
                .as("대기열 인원은 신청자 - 정원이어야 한다")
                .isEqualTo(CONTENDERS - CAPACITY);
    }
}
