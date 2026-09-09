package kr.haedal.ondal.qna.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kr.haedal.ondal.auth.LoginUser;
import kr.haedal.ondal.auth.authorization.CohortRole;
import kr.haedal.ondal.enrollment.entity.EnrollmentRole;
import kr.haedal.ondal.qna.dto.QuestionCreateRequest;
import kr.haedal.ondal.qna.dto.QuestionResponse;
import kr.haedal.ondal.qna.dto.QuestionUpdateRequest;
import kr.haedal.ondal.qna.service.QuestionService;
import kr.haedal.ondal.user.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Q&A 게시판 API (#23~#27) - 조회·등록은 분반 소속 누구나, 수정은 작성자 본인, 삭제는 작성자 또는 운영진 이상.
 * 어노테이션은 분반 소속까지만 본다 - "본인인가 / 운영진인가"는 서비스 몫 (Submission 선례).
 * 메서드마다: 권한 어노테이션 → 검증(@Valid) → 서비스 호출 → 서비스가 준 DTO 반환. 그 외 로직 없음.
 */
@Tag(name = "Question", description = "Q&A 게시판 - 조회·등록은 분반 소속자, 수정은 작성자, 삭제는 작성자 또는 운영진 이상")
@RestController
@RequestMapping("/api/cohorts/{cohortId}/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @Operation(summary = "질문 목록 - 최신순. canEdit·canDelete 는 요청자 의존")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping
    public List<QuestionResponse> list(@PathVariable Long cohortId, @LoginUser User user) {
        return questionService.findAll(cohortId, user);
    }

    @Operation(summary = "질문 상세")
    @CohortRole(EnrollmentRole.STUDENT)
    @GetMapping("/{questionId}")
    public QuestionResponse get(@PathVariable Long cohortId, @PathVariable Long questionId, @LoginUser User user) {
        return questionService.findOne(cohortId, questionId, user);
    }

    @Operation(summary = "질문 등록 - 분반 소속 누구나. 작성자는 요청자 본인. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.STUDENT)
    @PostMapping
    public ResponseEntity<QuestionResponse> create(@PathVariable Long cohortId,
                                                   @RequestBody @Valid QuestionCreateRequest request,
                                                   @LoginUser User user) {
        QuestionResponse created = questionService.create(cohortId, request, user);
        // 계약은 본문의 id. Location은 REST 관례상 덧붙이는 것 (CORS exposedHeaders 없이는 브라우저에서 못 읽음)
        return ResponseEntity.created(URI.create("/api/cohorts/" + cohortId + "/questions/" + created.id()))
                .body(created);
    }

    @Operation(summary = "질문 수정 (전체 교체) - 작성자 본인만, 아니면 403. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.STUDENT)
    @PutMapping("/{questionId}")
    public QuestionResponse update(@PathVariable Long cohortId,
                                   @PathVariable Long questionId,
                                   @RequestBody @Valid QuestionUpdateRequest request,
                                   @LoginUser User user) {
        return questionService.update(cohortId, questionId, request, user);
    }

    @Operation(summary = "질문 삭제 - 작성자 본인 또는 운영진 이상, 아니면 403. 보관 분반이면 409")
    @CohortRole(EnrollmentRole.STUDENT)
    @DeleteMapping("/{questionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long cohortId, @PathVariable Long questionId, @LoginUser User user) {
        questionService.delete(cohortId, questionId, user);
    }
}
