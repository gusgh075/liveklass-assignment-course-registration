package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.Waitlist;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.EnrollmentStatus;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.EnrollmentRepository;
import com.futureschole.course.repository.UserRepository;
import com.futureschole.course.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 수강 신청 도메인 서비스.
 *
 * <p>수강 신청을 받아 정원 상황에 따라 즉시 신청({@code PENDING} 생성)과 대기열 진입으로 분기한다.
 * 권한(역할) 검증은 컨트롤러에서 헤더 기반으로 끝나므로 본 서비스는 도메인 로직과 영속화에 집중한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class EnrollmentService {

    /** 정원에 포함되는 신청 상태. {@code CANCELLED}는 제외한다. */
    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    /** 결제 기한(분). {@code PENDING} 생성 시각으로부터 이 시간 안에 결제해야 한다. */
    private static final int PAYMENT_DEADLINE_MINUTES = 30;

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final Clock clock;

    /**
     * 수강 신청을 접수한다.
     *
     * <p>강의를 비관적 락으로 조회해 동시 신청의 정원 카운트 갱신을 직렬화한다. 강의가 {@code OPEN}이
     * 아니면 거부하고, 동일 사용자·강의의 활성 신청이나 대기열 진입이 이미 있으면 중복으로 거부한다.
     * 정원에 자리가 있으면 {@code PENDING} 신청을 만들어 결제 기한을 시작하고, 자리가 없으면 거부 대신
     * 대기열에 등록하고 순번을 반환한다.
     *
     * @param userId  신청 사용자의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param request 신청 대상 강의를 담은 요청 본문
     * @return 신청 결과(정원 내 신청 또는 대기열 진입)
     * @throws BusinessException 사용자가 없으면 {@link ErrorCode#USER_NOT_FOUND},
     *                           강의가 없으면 {@link ErrorCode#COURSE_NOT_FOUND},
     *                           강의가 {@code OPEN}이 아니면 {@link ErrorCode#COURSE_NOT_OPEN_FOR_ENROLLMENT},
     *                           활성 신청·대기열이 이미 있으면 {@link ErrorCode#DUPLICATE_ACTIVE_ENROLLMENT}
     */
    public EnrollmentCreateResponse apply(String userId, EnrollmentCreateRequest request) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Course course = courseRepository.findByIdForUpdate(request.courseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (course.getStatus() != CourseStatus.OPEN) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OPEN_FOR_ENROLLMENT);
        }
        if (enrollmentRepository.existsByUserAndCourseAndStatusIn(user, course, ACTIVE_STATUSES)
                || waitlistRepository.existsByUserAndCourse(user, course)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_ENROLLMENT);
        }

        int activeCount = enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE_STATUSES);
        if (activeCount < course.getCapacity()) {
            return enroll(user, course);
        }
        return joinWaitlist(user, course);
    }

    /** 정원 내 신청: {@code PENDING} 레코드를 만들고 생성 시각 + 30분을 결제 기한으로 반환한다. */
    private EnrollmentCreateResponse enroll(User user, Course course) {
        Enrollment saved = enrollmentRepository.save(Enrollment.pending(user, course));
        LocalDateTime paymentDeadline = saved.getCreatedAt().plusMinutes(PAYMENT_DEADLINE_MINUTES);
        return EnrollmentCreateResponse.enrolled(saved.getId(), course.getId(), paymentDeadline);
    }

    /** 정원 마감: 대기열에 등록하고 현재 대기 인원 수를 1-based 순번으로 반환한다. */
    private EnrollmentCreateResponse joinWaitlist(User user, Course course) {
        Waitlist saved = waitlistRepository.save(Waitlist.enqueue(user, course, LocalDateTime.now(clock)));
        int position = waitlistRepository.countByCourse(course);
        return EnrollmentCreateResponse.waitlisted(saved.getId(), course.getId(), position);
    }
}
