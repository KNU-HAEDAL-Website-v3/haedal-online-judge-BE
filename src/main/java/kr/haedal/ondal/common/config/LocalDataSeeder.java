package kr.haedal.ondal.common.config;

import kr.haedal.ondal.assignment.entity.Assignment;
import kr.haedal.ondal.assignment.repository.AssignmentRepository;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.qna.entity.Question;
import kr.haedal.ondal.qna.repository.QuestionRepository;
import kr.haedal.ondal.submission.entity.Submission;
import kr.haedal.ondal.submission.entity.SubmissionType;
import kr.haedal.ondal.submission.repository.SubmissionRepository;
import kr.haedal.ondal.user.entity.User;
import kr.haedal.ondal.user.repository.UserRepository;
import kr.haedal.ondal.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * local 프로필 전용 부트스트랩 - permissions.md 4절 "최초 관리자"의 개발 환경 버전 + 프론트 개발용 샘플 분반.
 * 운영 배포에서는 이 시더가 돌지 않으며, 실제 계정에 수동 SQL로 ADMIN을 지정한다.
 * 테스트(@ActiveProfiles("test"))에서도 돌지 않는다 - 테스트 픽스처는 각 테스트가 직접 만든다.
 *
 * 만드는 계정: admin(ADMIN) / operator1 / student1, student2, student3 (전부 스텁 로그인으로 바로 로그인 가능)
 * 만드는 분반: "2026-2 C언어"(ACTIVE: operator1 + student1~3), "2026-1 파이썬"(ARCHIVED: student1)
 * 만드는 과제: 진행 중 분반에 3개 - 1차시(마감 지남) · 2차시(마감 전) · 차시 없음 (FE가 그룹핑·정렬·D-day까지 바로 확인)
 *   문구는 자체 문제 서술 - Ondal은 자체 채점 OJ라 외부 사이트 풀이 지시를 쓰지 않는다 (docs/submission/design.md 결정 16)
 * 만드는 제출: 1차시 과제에 상태 4종 재현 - student1 제출(CODE) / student2 제출(추가)(CODE→LINK) / student3 지각(LINK), 2차시는 student1만 제출(나머지 미제출)
 *   (FILE 제출은 시딩하지 않는다 - 디스크 파일이 필요해 시더 부적합. CODE·LINK 제출만)
 * 만드는 질문: Q&A 게시판 2건 - student1·student2가 진행 중 분반에 작성 (목록 최신순·작성자 직책·수정/삭제 버튼 분기 확인용)
 */
@Component
@Profile("local")
public class LocalDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDataSeeder.class);

    private final UserRepository userRepository;
    private final UserService userService;
    private final CohortRepository cohortRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final AssignmentRepository assignmentRepository;
    private final SubmissionRepository submissionRepository;
    private final QuestionRepository questionRepository;

    public LocalDataSeeder(UserRepository userRepository,
                           UserService userService,
                           CohortRepository cohortRepository,
                           EnrollmentRepository enrollmentRepository,
                           AssignmentRepository assignmentRepository,
                           SubmissionRepository submissionRepository,
                           QuestionRepository questionRepository) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.cohortRepository = cohortRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.questionRepository = questionRepository;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByLoginId("admin").isEmpty()) {
            userRepository.save(User.admin("admin", "관리자"));
            log.info("[seed] local 관리자 계정 생성: loginId=admin");
        }
        if (cohortRepository.count() > 0) {
            return; // 이미 분반이 있으면 샘플을 다시 만들지 않는다
        }

        Cohort current = cohortRepository.save(Cohort.create("2026-2 C언어", "샘플 분반 (진행 중)"));
        enroll(current, "operator1", EnrollmentRole.OPERATOR);
        for (String loginId : List.of("student1", "student2", "student3")) {
            enroll(current, loginId, EnrollmentRole.STUDENT);
        }

        Cohort past = Cohort.create("2026-1 파이썬", "샘플 분반 (보관됨)");
        past.archive();
        cohortRepository.save(past);
        enroll(past, "student1", EnrollmentRole.STUDENT);

        Instant now = Instant.now();
        Assignment session1 = assignmentRepository.save(Assignment.create(current, 1000, 1, "1차시 - 입출력 연습",
                "두 정수 A와 B를 한 줄에 공백으로 구분해 입력받아 A+B를 출력하는 프로그램을 작성해 제출하세요.",
                now.minus(3, ChronoUnit.DAYS)));
        Assignment session2 = assignmentRepository.save(Assignment.create(current, 1001, 2, "2차시 - 조건문과 반복문",
                "정수 N을 입력받아 N단 구구단을 출력하는 문제와, 점수를 입력받아 등급(A~F)을 출력하는 문제를 풀어 제출하세요.",
                now.plus(7, ChronoUnit.DAYS)));
        assignmentRepository.save(Assignment.create(current, 1002, null, "설문 - 스터디 시간 조사",
                "차시와 무관한 공지형 과제입니다. 설문 링크를 확인하세요.",
                now.plus(14, ChronoUnit.DAYS)));

        seedSubmissions(session1, session2, now);
        seedQuestions(current);

        log.info("[seed] 샘플 분반 생성: '{}'(ACTIVE, 과제 3개 + 제출 시나리오 4종), '{}'(ARCHIVED). 계정: operator1, student1~3",
                current.getName(), past.getName());
    }

    /** 1차시(마감 = now-3d) 기준 상태 4종 재현 - createAt으로 과거 제출 시각을 지정한다 (시더 전용 경로) */
    private void seedSubmissions(Assignment session1, Assignment session2, Instant now) {
        User student1 = userService.findOrCreateMember("student1");
        User student2 = userService.findOrCreateMember("student2");
        User student3 = userService.findOrCreateMember("student3");

        String sampleCode = "#include <stdio.h>\n\nint main(void) {\n    int a, b;\n    scanf(\"%d %d\", &a, &b);\n    printf(\"%d\\n\", a + b);\n    return 0;\n}\n";

        // student1: 마감 내 1회(CODE) → 제출(SUBMITTED)
        submissionRepository.save(Submission.createAt(session1, student1, SubmissionType.CODE, sampleCode, "C",
                null, now.minus(5, ChronoUnit.DAYS)));
        // student2: 마감 내(CODE) + 마감 후 재제출(LINK 다중) → 제출(추가)(SUBMITTED_EXTRA)
        submissionRepository.save(Submission.createAt(session1, student2, SubmissionType.CODE, sampleCode, "C",
                null, now.minus(4, ChronoUnit.DAYS)));
        submissionRepository.save(Submission.createAt(session1, student2, SubmissionType.LINK, null, null,
                List.of("https://github.com/example/aplusb", "https://aplusb.example.dev"),
                now.minus(1, ChronoUnit.DAYS)));
        // student3: 마감 후만(LINK) → 지각(LATE)
        submissionRepository.save(Submission.createAt(session1, student3, SubmissionType.LINK, null, null,
                List.of("https://github.com/example/late-submit"), now.minus(1, ChronoUnit.DAYS)));
        // 2차시(마감 전): student1만 제출 → 나머지는 미제출(NOT_SUBMITTED) 확인용
        submissionRepository.save(Submission.createAt(session2, student1, SubmissionType.CODE, sampleCode, "C",
                null, now.minus(1, ChronoUnit.HOURS)));
    }

    /** Q&A 게시판 샘플 - 수강생 질문 2건. 답변(댓글)은 이 슬라이스 범위 밖이라 시딩하지 않는다 */
    private void seedQuestions(Cohort current) {
        User student1 = userService.findOrCreateMember("student1");
        User student2 = userService.findOrCreateMember("student2");
        questionRepository.save(Question.create(current, student1, "1차시 과제 입력 형식 질문",
                "A와 B가 한 줄에 공백으로 들어온다고 했는데, 줄바꿈으로 나뉘어 들어오는 경우도 처리해야 하나요?"));
        questionRepository.save(Question.create(current, student2, "제출 후 코드를 수정하면 어떻게 되나요?",
                "이미 제출한 과제의 코드를 고쳐 다시 제출하면 이전 제출은 사라지나요, 아니면 이력이 남나요?"));
    }

    private void enroll(Cohort cohort, String loginId, EnrollmentRole role) {
        User user = userService.findOrCreateMember(loginId);
        enrollmentRepository.save(Enrollment.create(cohort, user, role));
    }
}
