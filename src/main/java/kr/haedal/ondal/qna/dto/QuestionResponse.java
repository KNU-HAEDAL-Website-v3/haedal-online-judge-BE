package kr.haedal.ondal.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import kr.haedal.ondal.qna.entity.Question;
import kr.haedal.ondal.user.dto.UserSummary;

import java.time.Instant;

/**
 * 질문 응답 - 목록·단건·등록·수정 응답이 전부 이 하나의 모양이다.
 * author 의 직책과 canEdit·canDelete 는 요청자·분반 상태에 따라 달라지므로 QuestionResponseAssembler 가 채운다.
 */
public record QuestionResponse(
        Long id,
        String title,
        String content,

        @Schema(description = "작성자 - id·이름·직책 명칭만 (UserSummary). 수강생에게도 내려가는 타인 정보이므로 loginId 는 싣지 않는다")
        UserSummary author,

        Instant createdAt,

        @Schema(description = "요청자가 이 글을 수정할 수 있는가 - 작성자 본인이고 분반이 ACTIVE 일 때 true. 프론트는 이 값만 보고 수정 버튼을 분기한다")
        boolean canEdit,

        @Schema(description = "요청자가 이 글을 삭제할 수 있는가 - 작성자 본인 또는 운영진 이상(관리자 포함)이고 분반이 ACTIVE 일 때 true")
        boolean canDelete
) {
    /** 여러 값을 조합하므로 from(entity)가 아니라 of(...) */
    public static QuestionResponse of(Question question, UserSummary author, boolean canEdit, boolean canDelete) {
        return new QuestionResponse(
                question.getId(),
                question.getTitle(),
                question.getContent(),
                author,
                question.getCreatedAt(),
                canEdit,
                canDelete
        );
    }
}
