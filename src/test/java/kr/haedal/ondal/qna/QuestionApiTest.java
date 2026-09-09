package kr.haedal.ondal.qna;

import kr.haedal.ondal.support.ApiTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Q&A 게시판 API (QuestionController #23~#27) */
class QuestionApiTest extends ApiTestSupport {

    // ---- 슬라이스 고유 픽스처 (support/는 PM 파일 - 여기 private 헬퍼로) ----------------------

    private Map<String, Object> questionBody(String title) {
        return Map.of("title", title, "content", title + " 내용");
    }

    /** 세션 주인이 작성자가 된다 - 등록 권한은 분반 소속 누구나 */
    private long createQuestion(long cohortId, MockHttpSession author, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cohorts/{id}/questions", cohortId)
                        .session(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(questionBody(title))))
                .andExpect(status().isCreated())
                .andReturn();
        return readJson(result).get("id").asLong();
    }

    @Nested
    @DisplayName("인증과 권한 - 역할 x 엔드포인트")
    class Authorization {

        @Test
        void 미로그인이면_401() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/questions", id))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        void 비소속_부원은_조회도_등록도_403() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("outsider")))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("outsider"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("남의 반 질문"))))
                    .andExpect(status().isForbidden());
        }

        @Test
        void 수강생은_등록_목록_상세_모두_가능() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long questionId = createQuestion(id, login.member("s1"), "수강생 질문");

            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("수강생 질문"));
        }

        @Test
        void 수정은_작성자만_다른_수강생_운영진_관리자_모두_403() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long questionId = createQuestion(id, login.member("s1"), "s1의 질문");

            for (MockHttpSession other : new MockHttpSession[]{login.member("s2"), login.member("op1"), login.admin()}) {
                mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}", id, questionId)
                                .session(other)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(questionBody("남의 글 수정 시도"))))
                        .andExpect(status().isForbidden())
                        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            }
            mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}", id, questionId)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("본인 수정"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("본인 수정"));
        }

        @Test
        void 삭제는_작성자_또는_운영진_이상() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long byS1 = createQuestion(id, login.member("s1"), "s1의 질문");
            long byS2 = createQuestion(id, login.member("s2"), "s2의 질문");
            long byS1Again = createQuestion(id, login.member("s1"), "s1의 둘째 질문");

            // 다른 수강생은 403
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", id, byS1).session(login.member("s2")))
                    .andExpect(status().isForbidden());
            // 작성자 본인 204
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", id, byS1).session(login.member("s1")))
                    .andExpect(status().isNoContent());
            // 운영진은 남의 글도 204
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", id, byS2).session(login.member("op1")))
                    .andExpect(status().isNoContent());
            // 비소속 관리자도 204
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", id, byS1Again).session(login.admin()))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 비소속_관리자는_조회_등록_통과() throws Exception {
            long id = createCohort("C언어", "op1");
            createQuestion(id, login.member("op1"), "운영진 공지성 질문");
            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.admin()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.admin())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("관리자 질문"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.author.title").value("해구르르"));
        }
    }

    @Nested
    @DisplayName("스코프 조회 - 다른 분반의 질문은 존재를 드러내지 않는다")
    class Scope {

        @Test
        void 다른_분반의_질문은_GET_PUT_DELETE_모두_404() throws Exception {
            long a = createCohort("A반", "op1");
            long b = createCohort("B반", "op2");
            long bQuestion = createQuestion(b, login.member("op2"), "B반 질문");

            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", a, bQuestion).session(login.member("op1")))
                    .andExpect(status().isNotFound());
            mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}", a, bQuestion)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("탈취 시도"))))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", a, bQuestion).session(login.member("op1")))
                    .andExpect(status().isNotFound());

            // B반 질문은 그대로 남아 있다
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", b, bQuestion).session(login.member("op2")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("B반 질문"));
        }

        @Test
        void 없는_질문id는_404() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, 999_999).session(login.member("op1")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void 없는_분반은_관리자_404_부원_403() throws Exception {
            mockMvc.perform(get("/api/cohorts/{id}/questions", 999_999).session(login.admin()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get("/api/cohorts/{id}/questions", 999_999).session(login.member("nobody")))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        void 경로의_cohortId가_숫자가_아니면_400() throws Exception {
            mockMvc.perform(get("/api/cohorts/abc/questions").session(login.admin()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        }

        @Test
        void 제목_공백이나_200자_초과는_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody(" "))))
                    .andExpect(status().isBadRequest());
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("가".repeat(201)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 내용_공백이나_10000자_초과는_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "제목", "content", " "))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(containsString("content")));
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "제목", "content", "가".repeat(10001)))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void 깨진_JSON은_400() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("op1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ broken"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("보관 분반 - 열람 유지, 쓰기는 409")
    class ArchivedCohort {

        @Test
        void 보관되면_조회는_200_쓰기는_409_해제하면_다시_가능() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long questionId = createQuestion(id, login.member("s1"), "보관 전 질문");
            archiveCohort(id);

            // 열람은 그대로 - 단, 버튼 판정값은 전부 false
            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].canEdit").value(false))
                    .andExpect(jsonPath("$[0].canDelete").value(false));
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canDelete").value(false));

            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("보관 중 질문"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("COHORT_ARCHIVED"));
            mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}", id, questionId)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("보관 중 수정"))))
                    .andExpect(status().isConflict());
            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.admin()))
                    .andExpect(status().isConflict());

            restoreCohort(id);
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("복구 후 질문"))))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("동작 확인")
    class Behavior {

        @Test
        void 등록_응답에_Location_헤더와_작성자_요약이_있다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            mockMvc.perform(post("/api/cohorts/{id}/questions", id)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(questionBody("첫 질문"))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", containsString("/questions/")))
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.title").value("첫 질문"))
                    .andExpect(jsonPath("$.content").value("첫 질문 내용"))
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andExpect(jsonPath("$.author.name").value("s1"))
                    .andExpect(jsonPath("$.author.title").value("일반 수강생"))
                    .andExpect(jsonPath("$.author.loginId").doesNotExist())
                    .andExpect(jsonPath("$.canEdit").value(true))
                    .andExpect(jsonPath("$.canDelete").value(true));
        }

        @Test
        void 작성자_직책은_분반_역할을_따른다() throws Exception {
            long id = createCohort("C언어", "op1");
            long byOperator = createQuestion(id, login.member("op1"), "운영진 질문");
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, byOperator).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.author.title").value("교육운영진"));
        }

        @Test
        void 남의_글은_canEdit_false_운영진은_canDelete_true() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            enrollStudent(id, "s2");
            long questionId = createQuestion(id, login.member("s1"), "s1의 질문");

            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s2")))
                    .andExpect(jsonPath("$.canEdit").value(false))
                    .andExpect(jsonPath("$.canDelete").value(false));
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("op1")))
                    .andExpect(jsonPath("$.canEdit").value(false))
                    .andExpect(jsonPath("$.canDelete").value(true));
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.admin()))
                    .andExpect(jsonPath("$.canEdit").value(false))
                    .andExpect(jsonPath("$.canDelete").value(true));
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s1")))
                    .andExpect(jsonPath("$.canEdit").value(true))
                    .andExpect(jsonPath("$.canDelete").value(true));
        }

        @Test
        void 수정하면_재조회에_반영된다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long questionId = createQuestion(id, login.member("s1"), "원래 제목");

            mockMvc.perform(put("/api/cohorts/{id}/questions/{qid}", id, questionId)
                            .session(login.member("s1"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("title", "바뀐 제목", "content", "바뀐 내용"))))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("바뀐 제목"))
                    .andExpect(jsonPath("$.content").value("바뀐 내용"))
                    .andExpect(jsonPath("$.author.name").value("s1"));
        }

        @Test
        void 목록은_최신순() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            createQuestion(id, login.member("s1"), "질문A");
            createQuestion(id, login.member("op1"), "질문B");
            createQuestion(id, login.member("s1"), "질문C");

            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].title").value("질문C"))
                    .andExpect(jsonPath("$[1].title").value("질문B"))
                    .andExpect(jsonPath("$[1].author.title").value("교육운영진"))
                    .andExpect(jsonPath("$[2].title").value("질문A"));
        }

        @Test
        void 질문이_없으면_빈_배열() throws Exception {
            long id = createCohort("C언어", "op1");
            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("op1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        void 삭제하면_목록에서_사라진다() throws Exception {
            long id = createCohort("C언어", "op1");
            enrollStudent(id, "s1");
            long questionId = createQuestion(id, login.member("s1"), "지울 질문");

            mockMvc.perform(delete("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s1")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/cohorts/{id}/questions", id).session(login.member("s1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
            mockMvc.perform(get("/api/cohorts/{id}/questions/{qid}", id, questionId).session(login.member("s1")))
                    .andExpect(status().isNotFound());
        }
    }
}
