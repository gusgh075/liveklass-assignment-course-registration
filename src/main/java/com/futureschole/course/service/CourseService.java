package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.CourseStatus;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 강의 도메인 서비스.
 *
 * <p>강의 등록·수정·상태 변경 등 강의 단건 처리를 담당한다. 권한(역할) 검증은 컨트롤러에서
 * 헤더 기반으로 끝나므로 본 서비스는 도메인 로직과 영속화에만 집중한다.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CourseService {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;

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
}
