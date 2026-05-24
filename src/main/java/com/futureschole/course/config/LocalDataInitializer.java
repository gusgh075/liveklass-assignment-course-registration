package com.futureschole.course.config;

import com.futureschole.course.entity.Course;
import com.futureschole.course.entity.Enrollment;
import com.futureschole.course.entity.User;
import com.futureschole.course.entity.type.UserRole;
import com.futureschole.course.repository.CourseRepository;
import com.futureschole.course.repository.EnrollmentRepository;
import com.futureschole.course.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 로컬 데모용 시드 데이터 초기화기.
 *
 * <p>{@code local} 프로파일에서만 동작한다(`--spring.profiles.active=local`). 회원가입 API가 없어
 * 사용자 행이 미리 있어야 강의 등록·수강 신청 API를 호출할 수 있으므로, 빈 in-memory DB에 샘플 사용자·
 * 강의·신청을 넣어 부팅 직후 Swagger UI 등에서 바로 시연할 수 있게 한다. 테스트는 이 프로파일을 켜지
 * 않으므로 시더 빈이 생성되지 않아 영향이 없다.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class LocalDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final CourseRepository courseRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }

        User creator = userRepository.save(user("creator-001", UserRole.ROLE_CREATOR));
        User user1 = userRepository.save(user("user-001", UserRole.ROLE_USER));
        userRepository.save(user("user-002", UserRole.ROLE_USER));
        userRepository.save(user("user-003", UserRole.ROLE_USER));

        LocalDateTime start = LocalDateTime.now().plusDays(7);
        LocalDateTime end = LocalDateTime.now().plusMonths(2);

        Course knifeSkills = courseRepository.save(
                open(Course.draftOf(creator, "요리 초보자를 위한 기본 칼질과 불 조절",
                        "주방 입문자를 위한 4주 기초 클래스. 칼 잡는 법부터 화력 조절까지 다룹니다.", 49000, 30, start, end)));
        courseRepository.save(
                open(Course.draftOf(creator, "혼밥러를 위한 10분 한 그릇 요리",
                        "정원이 작아 대기열을 시연하기 좋은 강의. 바쁜 1인 가구를 위한 초간단 레시피.", 79000, 2, start, end)));
        courseRepository.save(
                Course.draftOf(creator, "프랑스 가정식 마스터 클래스 (작성 중)",
                        "아직 임시저장 상태라 목록에 노출되지 않는 강의.", 59000, 20, start, end));

        // user-001이 '기본 칼질' 강의에 PENDING 신청 — 결제 확정·내 신청 목록 시연용.
        enrollmentRepository.save(Enrollment.pending(user1, knifeSkills));

        log.info("[local] 시드 데이터 생성 완료 — 사용자 4명, 강의 3개(OPEN 2 / DRAFT 1), PENDING 신청 1건");
    }

    private User user(String userId, UserRole role) {
        return User.builder().userId(userId).role(role).build();
    }

    private Course open(Course course) {
        course.open();
        return course;
    }
}
