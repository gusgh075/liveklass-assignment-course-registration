package com.futureschole.course.service;

import com.futureschole.course.common.BusinessException;
import com.futureschole.course.common.ErrorCode;
import com.futureschole.course.dto.request.CourseCreateRequest;
import com.futureschole.course.dto.response.CourseDetailResponse;
import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.User;
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
}
