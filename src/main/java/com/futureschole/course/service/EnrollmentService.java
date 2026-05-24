package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.EnrollmentCreateRequest;
import com.futureschole.course.dto.response.EnrollmentCreateResponse;
import com.futureschole.course.dto.response.EnrollmentResponse;
import com.futureschole.course.dto.response.MyEnrollmentItemResponse;
import com.futureschole.course.dto.response.PageMyEnrollmentItem;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

    /** 환불 가능 기간(일). 결제 확정 시각으로부터 이 기간 안에만 수강 취소를 허용한다. */
    private static final int REFUND_WINDOW_DAYS = 7;

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

    /**
     * 본인의 결제 대기 신청을 결제 확정으로 전이한다.
     *
     * <p>신청을 조회해 본인 소유인지 확인하고, 상태가 {@code PENDING}이며 결제 기한 30분이 지나지
     * 않았을 때만 {@code CONFIRMED}로 전이한다. 확정 시각이 기록되며 이는 환불 기준 시각이 된다.
     * 영속 상태의 엔티티를 변경하므로 더티 체킹으로 반영되고 별도 저장 호출은 하지 않는다.
     *
     * @param userId       요청 사용자의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param enrollmentId 결제 확정 대상 신청 식별자
     * @return 확정 후 신청 상태를 담은 응답
     * @throws BusinessException 신청이 없으면 {@link ErrorCode#ENROLLMENT_NOT_FOUND},
     *                           본인 신청이 아니면 {@link ErrorCode#ENROLLMENT_NOT_OWNED},
     *                           상태가 {@code PENDING}이 아니면 {@link ErrorCode#INVALID_STATUS_FOR_CONFIRM},
     *                           결제 기한이 지났으면 {@link ErrorCode#PAYMENT_DEADLINE_EXPIRED}
     */
    public EnrollmentResponse confirm(String userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_OWNED);
        }
        if (enrollment.getStatus() != EnrollmentStatus.PENDING) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_FOR_CONFIRM);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime paymentDeadline = enrollment.getCreatedAt().plusMinutes(PAYMENT_DEADLINE_MINUTES);
        if (paymentDeadline.isBefore(now)) {
            throw new BusinessException(ErrorCode.PAYMENT_DEADLINE_EXPIRED);
        }

        enrollment.confirm(now);
        return EnrollmentResponse.from(enrollment);
    }

    /**
     * 본인의 결제 확정 신청을 수강 취소로 전이한다.
     *
     * <p>신청을 조회해 본인 소유인지 확인하고, 상태가 {@code CONFIRMED}이며 결제 확정 시각으로부터
     * 7일이 지나지 않았을 때만 {@code CANCELLED}로 전이한다. 취소 시각이 기록되고 정원 산정에서
     * 제외된다. 영속 상태의 엔티티를 변경하므로 더티 체킹으로 반영되고 별도 저장 호출은 하지 않는다.
     *
     * @param userId       요청 사용자의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param enrollmentId 수강 취소 대상 신청 식별자
     * @return 취소 후 신청 상태를 담은 응답
     * @throws BusinessException 신청이 없으면 {@link ErrorCode#ENROLLMENT_NOT_FOUND},
     *                           본인 신청이 아니면 {@link ErrorCode#ENROLLMENT_NOT_OWNED},
     *                           상태가 {@code CONFIRMED}가 아니면 {@link ErrorCode#INVALID_STATUS_FOR_CANCEL},
     *                           결제 확정 후 7일이 지났으면 {@link ErrorCode#REFUND_WINDOW_EXPIRED}
     */
    public EnrollmentResponse cancel(String userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_OWNED);
        }
        if (enrollment.getStatus() != EnrollmentStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_FOR_CANCEL);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime refundDeadline = enrollment.getConfirmedAt().plusDays(REFUND_WINDOW_DAYS);
        if (refundDeadline.isBefore(now)) {
            throw new BusinessException(ErrorCode.REFUND_WINDOW_EXPIRED);
        }

        enrollment.cancel(now);
        return EnrollmentResponse.from(enrollment);
    }

    /**
     * 본인의 수강 신청 이력을 페이지로 조회한다.
     *
     * <p>{@code PENDING}/{@code CONFIRMED}/{@code CANCELLED}를 가리지 않고 본인 신청 전체를 반환한다.
     * 각 항목은 신청 상태와 함께 대상 강의의 제목·가격·정원을 담으며, 연관 강의는 Repository에서
     * 함께 로드한다.
     *
     * @param userId   요청 사용자의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param pageable 페이지 요청(번호·크기·정렬)
     * @return 내 신청 목록 페이지 응답
     * @throws BusinessException 사용자가 없으면 {@link ErrorCode#USER_NOT_FOUND}
     */
    @Transactional(readOnly = true)
    public PageMyEnrollmentItem getMyEnrollments(String userId, Pageable pageable) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Enrollment> page = enrollmentRepository.findByUser(user, pageable);
        return PageMyEnrollmentItem.from(page, MyEnrollmentItemResponse::from);
    }

    /**
     * 결제 기한이 지난 결제 대기 신청을 일괄 취소한다.
     *
     * <p>스케줄러가 주기적으로 호출한다. {@link EnrollmentStatus#PENDING} 중 생성 후 결제 기한(30분)이
     * 지난 신청을 찾아 {@link EnrollmentStatus#CANCELLED}로 전이한다(취소 시각 기록). 더티 체킹으로 반영된다.
     *
     * @return 만료 처리된 신청 수
     */
    public int expirePendingPayments() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime threshold = now.minusMinutes(PAYMENT_DEADLINE_MINUTES);
        List<Enrollment> expired = enrollmentRepository.findByStatusAndCreatedAtBefore(
                EnrollmentStatus.PENDING, threshold);
        expired.forEach(enrollment -> enrollment.cancel(now));
        return expired.size();
    }

    /**
     * 대기열에 사람이 있는 강의의 식별자를 조회한다(자동 승급 대상 선별용).
     *
     * @return 대기 인원이 있는 강의 식별자 목록
     */
    @Transactional(readOnly = true)
    public List<Long> findWaitlistedCourseIds() {
        return waitlistRepository.findDistinctCourseIds();
    }

    /**
     * 한 강의의 빈 자리만큼 대기열 헤드를 결제 대기 신청으로 승급한다.
     *
     * <p>강의를 비관적 락으로 조회해 신규 신청과 정원 다툼을 직렬화한다. 강의가 {@code OPEN}일 때만
     * 처리하며, (정원 - 활성 신청 수)만큼 진입 시각이 이른 대기자부터 새 {@code PENDING} 신청을 만들고
     * (결제 기한 30분이 새로 시작) 해당 대기열 레코드를 제거한다. 자리가 없거나 강의가 모집 상태가 아니면
     * 아무 일도 하지 않는다.
     *
     * @param courseId 승급을 시도할 강의 식별자
     */
    public void promoteCourse(Long courseId) {
        Course course = courseRepository.findByIdForUpdate(courseId).orElse(null);
        if (course == null || course.getStatus() != CourseStatus.OPEN) {
            return;
        }
        int activeCount = enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE_STATUSES);
        int available = course.getCapacity() - activeCount;
        if (available <= 0) {
            return;
        }
        List<Waitlist> heads = waitlistRepository.findByCourseOrderByEnteredAtAsc(
                course, PageRequest.of(0, available));
        for (Waitlist head : heads) {
            enrollmentRepository.save(Enrollment.pending(head.getUser(), course));
            waitlistRepository.delete(head);
        }
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
