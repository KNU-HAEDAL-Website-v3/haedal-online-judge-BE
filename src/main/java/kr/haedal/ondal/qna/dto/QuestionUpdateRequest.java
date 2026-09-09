package kr.haedal.ondal.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 질문 수정 요청 (PUT 전체 교체) - 필드·검증은 등록 요청과 동일. 작성자·분반은 바꿀 수 없다 */
public record QuestionUpdateRequest(
        @Schema(description = "질문 제목")
        @NotBlank(message = "질문 제목은 비어 있을 수 없습니다.")
        @Size(max = 200, message = "질문 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "질문 내용 - 자유 텍스트")
        @NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
        @Size(max = 10000, message = "질문 내용은 10000자 이하여야 합니다.")
        String content
) {
}
