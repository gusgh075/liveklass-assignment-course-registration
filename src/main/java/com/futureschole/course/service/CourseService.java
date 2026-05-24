package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.dto.response.CourseEnrollmentItemResponse;
import com.futureschole.course.dto.response.CourseSummaryResponse;
import com.futureschole.course.dto.response.PageCourseEnrollmentItem;
import com.futureschole.course.dto.response.PageCourseSummary;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.entity.type.EnrollmentStatus;
import com.futureschole.course.repository.CourseCountProjection;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.EnrollmentRepository;
import com.futureschole.course.repository.UserRepository;
import com.futureschole.course.repository.WaitlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 강의 도메인 서비스.
 *
 * <p>강의 등록·수정·상세·목록 조회 등 강의 처리를 담당한다. 권한(역할) 검증은 컨트롤러에서
 * 헤더 기반으로 끝나므로 본 서비스는 도메인 로직과 영속화에만 집중한다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CourseService {

    /** 정원에 포함되는 신청 상태. {@code CANCELLED}는 제외한다. */
    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final WaitlistRepository waitlistRepository;
    private final Clock clock;

    /**
     * 새 강의를 임시저장 상태로 등록한다.
     *
     * <p>외부 식별자 {@code userId}로 강사 사용자를 찾아 {@link Course#draftOf} 정적 팩토리로 강의를
     * 만들고 저장한 뒤, 응답 DTO로 변환해 반환한다. 생성 직후라 신청 인원과 대기 인원은 모두 0이다.
     *
     * @param userId  강사의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param request 강의 등록 요청 본문
     * @return 생성된 강의의 상세 응답
     * @throws BusinessException 외부 식별자에 해당하는 사용자가 없는 경우
     *                           ({@link ErrorCode#USER_NOT_FOUND})
     */
    @Transactional
    public CourseDetailResponse create(String userId, CourseCreateRequest request) {
        User creator = userRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        Course course = Course.draftOf(
                creator,
                request.title(),
                request.description(),
                request.price(),
                request.capacity(),
                request.startDate(),
                request.endDate()
        );
        Course saved = courseRepository.save(course);

        return CourseDetailResponse.from(saved, 0, 0);
    }

    /**
     * 임시저장 상태의 강의 정보를 새 요청 값으로 전체 교체한다.
     *
     * <p>강의를 조회한 뒤 요청자가 작성자 본인인지, 강의가 아직 {@link CourseStatus#DRAFT}인지를 차례로
     * 확인하고 통과하면 제목·설명·가격·정원·수강 기간을 통째로 바꾼다. 영속성 컨텍스트의 더티 체킹으로
     * 트랜잭션 커밋 시점에 반영되므로 별도 저장 호출은 하지 않는다.
     *
     * <p>{@code DRAFT} 강의는 신청을 받은 적이 없으므로 응답의 신청 인원과 대기 인원은 항상 0이다.
     *
     * @param userId   요청자의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param courseId 수정할 강의 식별자
     * @param request  교체할 강의 정보
     * @return 수정이 반영된 강의의 상세 응답
     * @throws BusinessException 강의가 없으면 {@link ErrorCode#COURSE_NOT_FOUND},
     *                           요청자가 작성자가 아니면 {@link ErrorCode#COURSE_NOT_OWNED},
     *                           {@code DRAFT}가 아닌 강의면 {@link ErrorCode#COURSE_NOT_EDITABLE}
     */
    @Transactional
    public CourseDetailResponse update(String userId, Long courseId, CourseCreateRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (!course.getCreator().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OWNED);
        }
        if (course.getStatus() != CourseStatus.DRAFT) {
            throw new BusinessException(ErrorCode.COURSE_NOT_EDITABLE);
        }

        course.modifyDraft(
                request.title(),
                request.description(),
                request.price(),
                request.capacity(),
                request.startDate(),
                request.endDate()
        );

        return CourseDetailResponse.from(course, 0, 0);
    }

    /**
     * 크리에이터가 자신의 강의를 오픈하거나 마감한다.
     *
     * <p>강의를 조회해 요청자가 작성자 본인인지 확인한 뒤, 현재 상태에서 목표 상태로의 전이가 적법한지
     * 검사한다. 허용하는 전이는 오픈({@code DRAFT → OPEN})과 마감({@code OPEN → CLOSED})뿐이며, 그
     * 밖의 모든 전이는 거부한다. 오픈하려는 강의의 종료일이 이미 지났다면 모집을 시작할 수 없으므로
     * 함께 막는다.
     *
     * <p>전이가 반영된 강의는 더티 체킹으로 영속화되며, 응답에는 상세 조회와 동일하게 활성 신청 인원
     * ({@link EnrollmentStatus#PENDING}+{@link EnrollmentStatus#CONFIRMED})과 대기열 인원을 합쳐 담는다.
     *
     * @param userId   요청자의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param courseId 상태를 변경할 강의 식별자
     * @param target   전이 목표 상태({@code OPEN} 또는 {@code CLOSED})
     * @return 상태 변경이 반영된 강의의 상세 응답
     * @throws BusinessException 강의가 없으면 {@link ErrorCode#COURSE_NOT_FOUND},
     *                           요청자가 작성자가 아니면 {@link ErrorCode#COURSE_NOT_OWNED},
     *                           허용되지 않은 전이면 {@link ErrorCode#COURSE_ILLEGAL_TRANSITION},
     *                           오픈 시점에 종료일이 이미 지났으면 {@link ErrorCode#COURSE_ENDED}
     */
    @Transactional
    public CourseDetailResponse changeStatus(String userId, Long courseId, CourseStatus target) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (!course.getCreator().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OWNED);
        }

        CourseStatus current = course.getStatus();
        if (current == CourseStatus.DRAFT && target == CourseStatus.OPEN) {
            if (course.getEndDate().isBefore(LocalDateTime.now(clock))) {
                throw new BusinessException(ErrorCode.COURSE_ENDED);
            }
            course.open();
        } else if (current == CourseStatus.OPEN && target == CourseStatus.CLOSED) {
            course.close();
        } else {
            throw new BusinessException(ErrorCode.COURSE_ILLEGAL_TRANSITION);
        }

        int enrolledCount = enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE_STATUSES);
        int waitingCount = waitlistRepository.countByCourse(course);
        return CourseDetailResponse.from(course, enrolledCount, waitingCount);
    }

    /**
     * 강의 상세 정보를 신청 인원·대기 인원과 함께 조회한다.
     *
     * <p>인증·권한을 따지지 않는 공개 조회로, 강의를 찾은 뒤 활성 신청 인원
     * ({@link EnrollmentStatus#PENDING}+{@link EnrollmentStatus#CONFIRMED})과 대기열 인원을 각각
     * 집계해 응답에 담는다.
     *
     * @param courseId 조회할 강의 식별자
     * @return 인원 카운트가 채워진 강의 상세 응답
     * @throws BusinessException 강의가 없으면 {@link ErrorCode#COURSE_NOT_FOUND}
     */
    public CourseDetailResponse getDetail(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        int enrolledCount = enrollmentRepository.countByCourseAndStatusIn(course, ACTIVE_STATUSES);
        int waitingCount = waitlistRepository.countByCourse(course);

        return CourseDetailResponse.from(course, enrolledCount, waitingCount);
    }

    /**
     * 강의 목록을 상태로 필터해 페이지 단위로 조회한다.
     *
     * <p>주어진 상태에 속하는 강의를 페이지로 조회한 뒤, 그 페이지에 담긴 강의 ID들을 한 번에 묶어
     * 활성 신청 인원({@code PENDING + CONFIRMED})과 대기열 인원을 각각 집계한다. 강의 건마다 카운트를
     * 따로 질의하지 않으므로 목록 크기와 무관하게 카운트 쿼리는 두 번으로 고정된다. 집계 결과에 없는
     * 강의는 인원이 0인 것으로 본다.
     *
     * @param statuses 조회 대상 상태 집합(목록 조회 기본은 {@code OPEN}+{@code CLOSED})
     * @param pageable 페이지·정렬 정보
     * @return 강의 목록 페이지 응답
     */
    public PageCourseSummary getList(List<CourseStatus> statuses, Pageable pageable) {
        Page<Course> page = courseRepository.findByStatusIn(statuses, pageable);

        List<Long> courseIds = page.getContent().stream()
                .map(Course::getId)
                .toList();
        Map<Long, Long> enrolledCounts = toCountMap(courseIds.isEmpty()
                ? List.of()
                : enrollmentRepository.countActiveByCourseIds(courseIds, ACTIVE_STATUSES));
        Map<Long, Long> waitingCounts = toCountMap(courseIds.isEmpty()
                ? List.of()
                : waitlistRepository.countByCourseIds(courseIds));

        Function<Course, CourseSummaryResponse> mapper = course -> CourseSummaryResponse.from(
                course,
                Math.toIntExact(enrolledCounts.getOrDefault(course.getId(), 0L)),
                Math.toIntExact(waitingCounts.getOrDefault(course.getId(), 0L)));
        return PageCourseSummary.from(page, mapper);
    }

    /**
     * 강사가 자신의 강의에 신청한 활성 수강생 목록을 페이지로 조회한다.
     *
     * <p>강의를 찾은 뒤 요청자가 작성자 본인인지 확인하고, 활성 신청
     * ({@link EnrollmentStatus#PENDING}+{@link EnrollmentStatus#CONFIRMED})만 페이지 단위로 모아 항목
     * 응답으로 변환한다. 취소된 신청은 목록에 담지 않는다. 권한(역할) 검증은 컨트롤러에서 끝나므로 본
     * 메서드는 강의 소유권만 검사한다.
     *
     * @param courseId         조회할 강의 식별자
     * @param requesterUserId  요청자의 외부 식별자({@code X-User-Id} 헤더 값)
     * @param pageable         페이지·정렬 정보
     * @return 활성 신청 항목으로 채워진 수강생 목록 페이지
     * @throws BusinessException 강의가 없으면 {@link ErrorCode#COURSE_NOT_FOUND},
     *                           요청자가 작성자가 아니면 {@link ErrorCode#COURSE_NOT_OWNED}
     */
    public PageCourseEnrollmentItem getCourseEnrollments(
            Long courseId, String requesterUserId, Pageable pageable) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        if (!course.getCreator().getUserId().equals(requesterUserId)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_OWNED);
        }

        Page<Enrollment> page = enrollmentRepository.findByCourseAndStatusIn(course, ACTIVE_STATUSES, pageable);
        return PageCourseEnrollmentItem.from(page, CourseEnrollmentItemResponse::from);
    }

    /**
     * 종료일이 지난 모집 중 강의를 일괄 마감한다.
     *
     * <p>스케줄러가 주기적으로 호출한다. {@link CourseStatus#OPEN} 중 종료일이 현재 시각보다 이른 강의를
     * 찾아 {@link CourseStatus#CLOSED}로 전이한다. 더티 체킹으로 반영된다.
     *
     * @return 마감 처리된 강의 수
     */
    @Transactional
    public int closeEndedCourses() {
        List<Course> ended = courseRepository.findByStatusAndEndDateBefore(
                CourseStatus.OPEN, LocalDateTime.now(clock));
        ended.forEach(Course::close);
        return ended.size();
    }

    /** 강의별 집계 프로젝션 목록을 {@code (강의 ID, 건수)} 맵으로 모은다. */
    private Map<Long, Long> toCountMap(List<CourseCountProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(CourseCountProjection::getCourseId, CourseCountProjection::getCount));
    }
}
