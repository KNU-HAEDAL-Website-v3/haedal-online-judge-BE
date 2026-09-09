package kr.haedal.ondal.qna.service;

import kr.haedal.ondal.auth.authorization.CohortAuthorizer;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.cohort.repository.CohortRepository;
import kr.haedal.ondal.common.error.ForbiddenException;
import kr.haedal.ondal.common.error.NotFoundException;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.qna.dto.QuestionCreateRequest;
import kr.haedal.ondal.qna.dto.QuestionResponse;
import kr.haedal.ondal.qna.dto.QuestionUpdateRequest;
import kr.haedal.ondal.qna.entity.Question;
import kr.haedal.ondal.qna.repository.QuestionRepository;
import kr.haedal.ondal.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Q&A 게시판 질문 CRUD - Assignment 슬라이스 패턴 복제.
 * - 하위 id 조회는 반드시 findByIdAndCohortId... - 경로의 cohortId 와 불일치(다른 반 글)·부재면 404 (존재 비노출)
 * - 쓰기는 첫 줄에서 cohort.ensureActive() - 보관 분반이면 409
 * - 어노테이션(@CohortRole(STUDENT))은 "분반 소속"까지만 보장한다. 작성자 본인·운영진 판정은 여기서 하고 아니면 403
 * - 응답 조립은 QuestionResponseAssembler - author 직책·canEdit·canDelete 가 요청자 의존이라 viewer 를 받는다
 */
@Service
@Transactional
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final CohortRepository cohortRepository;
    private final CohortAuthorizer cohortAuthorizer;
    private final QuestionResponseAssembler assembler;

    public QuestionService(QuestionRepository questionRepository,
                           CohortRepository cohortRepository,
                           CohortAuthorizer cohortAuthorizer,
                           QuestionResponseAssembler assembler) {
        this.questionRepository = questionRepository;
        this.cohortRepository = cohortRepository;
        this.cohortAuthorizer = cohortAuthorizer;
        this.assembler = assembler;
    }

    /** 목록 - 최신순. 보관 분반도 열람은 유지된다 */
    @Transactional(readOnly = true)
    public List<QuestionResponse> findAll(Long cohortId, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        return assembler.toResponses(questionRepository.findAllByCohortIdWithAuthor(cohortId), cohort, viewer);
    }

    @Transactional(readOnly = true)
    public QuestionResponse findOne(Long cohortId, Long questionId, User viewer) {
        Cohort cohort = requireCohort(cohortId);
        return assembler.toResponse(requireQuestion(cohortId, questionId), cohort, viewer);
    }

    /** 등록 - 분반 소속 누구나(수강생 포함). 작성자는 요청자 본인으로 고정된다 */
    public QuestionResponse create(Long cohortId, QuestionCreateRequest request, User author) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Question question = questionRepository.save(
                Question.create(cohort, author, request.title(), request.content()));
        return assembler.toResponse(question, cohort, author);
    }

    /** 수정 - 작성자 본인만. 운영진·관리자도 남의 글은 고칠 수 없다(삭제만 가능) */
    public QuestionResponse update(Long cohortId, Long questionId, QuestionUpdateRequest request, User editor) {
        Cohort cohort = requireCohort(cohortId);
        cohort.ensureActive();
        Question question = requireQuestion(cohortId, questionId);
        if (!question.isWrittenBy(editor)) {
            throw new ForbiddenException();
        }
        question.update(request.title(), request.content());
        return assembler.toResponse(question, cohort, editor);
    }

    /** 삭제 - 작성자 본인 또는 운영진 이상(관리자는 자동 통과). 게시판 정리는 운영 권한 */
    public void delete(Long cohortId, Long questionId, User requester) {
        requireCohort(cohortId).ensureActive();
        Question question = requireQuestion(cohortId, questionId);
        if (!question.isWrittenBy(requester)
                && !cohortAuthorizer.isAllowed(requester, cohortId, EnrollmentRole.OPERATOR)) {
            throw new ForbiddenException();
        }
        questionRepository.delete(question);
    }

    private Cohort requireCohort(Long cohortId) {
        return cohortRepository.findById(cohortId)
                .orElseThrow(() -> new NotFoundException("분반을 찾을 수 없습니다."));
    }

    private Question requireQuestion(Long cohortId, Long questionId) {
        return questionRepository.findByIdAndCohortIdWithAuthor(questionId, cohortId)
                .orElseThrow(() -> new NotFoundException("질문을 찾을 수 없습니다."));
    }
}
