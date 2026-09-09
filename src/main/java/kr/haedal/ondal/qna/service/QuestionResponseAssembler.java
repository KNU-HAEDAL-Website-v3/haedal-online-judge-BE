package kr.haedal.ondal.qna.service;

import kr.haedal.ondal.auth.authorization.CohortAuthorizer;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.enrollment.entity.Enrollment;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.enrollment.repository.EnrollmentRepository;
import kr.haedal.ondal.qna.dto.QuestionResponse;
import kr.haedal.ondal.qna.entity.Question;
import kr.haedal.ondal.user.dto.UserSummary;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Question 목록 → QuestionResponse 목록. 요청자에 따라 달라지는 필드(canEdit·canDelete)와 작성자 직책을 채운다.
 * AssignmentResponseAssembler 패턴 복제 - 질문 N개에 대해 분반 명부 조회는 1번으로 끝낸다.
 * 호출하는 쪽의 트랜잭션 안에서 실행된다는 전제 - 자체 @Transactional 은 없다. author 는 리포지토리가 fetch join 해 온다.
 *
 * - author.title: 작성자의 이 분반 역할(교육운영진/일반 수강생)로 정한다. 관리자는 역할과 무관하게 해구르르 (UserSummary 규칙)
 * - canEdit: 작성자 본인만. 운영진·관리자도 남의 글은 고칠 수 없다 - 남의 말을 바꾸는 것은 운영이 아니다
 * - canDelete: 작성자 본인 또는 운영진 이상 - 게시판 정리는 운영 권한(CohortAuthorizer.canManage 와 같은 규칙, 보관 분반이면 false)
 */
@Component
public class QuestionResponseAssembler {

    private final EnrollmentRepository enrollmentRepository;
    private final CohortAuthorizer cohortAuthorizer;

    public QuestionResponseAssembler(EnrollmentRepository enrollmentRepository, CohortAuthorizer cohortAuthorizer) {
        this.enrollmentRepository = enrollmentRepository;
        this.cohortAuthorizer = cohortAuthorizer;
    }

    public QuestionResponse toResponse(Question question, Cohort cohort, User viewer) {
        return toResponses(List.of(question), cohort, viewer).get(0);
    }

    public List<QuestionResponse> toResponses(List<Question> questions, Cohort cohort, User viewer) {
        if (questions.isEmpty()) {
            return List.of();
        }
        // 분반 명부 1번 조회 - 작성자들의 직책과 요청자의 역할을 여기서 함께 얻는다 (userId → role)
        Map<Long, EnrollmentRole> roles = enrollmentRepository.findAllByCohortIdWithUser(cohort.getId()).stream()
                .collect(Collectors.toMap(e -> e.getUser().getId(), Enrollment::getRole));
        EnrollmentRole myRole = roles.get(viewer.getId());
        boolean canModerate = cohortAuthorizer.canManage(viewer, cohort, myRole);

        return questions.stream()
                .map(question -> {
                    User author = question.getAuthor();
                    boolean canEdit = cohort.isActive() && question.isWrittenBy(viewer);
                    // 분반에서 빠진 작성자(roles 에 없음)는 일반 수강생으로 표시된다 - 글은 남고 직책만 사라진다
                    return QuestionResponse.of(question, UserSummary.of(author, roles.get(author.getId())),
                            canEdit, canEdit || canModerate);
                })
                .toList();
    }
}
