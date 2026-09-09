package kr.haedal.ondal.qna.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 질문 등록 요청. 검증 어노테이션은 요청 DTO 필드에만 단다. */
public record QuestionCreateRequest(
        @Schema(description = "질문 제목", example = "1차시 과제 입력 형식 질문")
        @NotBlank(message = "질문 제목은 비어 있을 수 없습니다.")
        @Size(max = 200, message = "질문 제목은 200자 이하여야 합니다.")
        String title,

        @Schema(description = "질문 내용 - 자유 텍스트")
        @NotBlank(message = "질문 내용은 비어 있을 수 없습니다.")
        @Size(max = 10000, message = "질문 내용은 10000자 이하여야 합니다.")
        String content
) {
}
