package kr.haedal.ondal.qna.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import kr.haedal.ondal.cohort.entity.Cohort;
import kr.haedal.ondal.user.entity.User;

import java.time.Instant;

/**
 * Q&A 게시판의 질문 글 - 분반 소속자가 자기 분반에 올리는 글. 분반 하나에 속하고 작성자 한 명을 가진다.
 * 조회는 항상 (id, cohort_id) 스코프 - 다른 분반의 글은 존재를 드러내지 않는다(404). (Assignment 선례)
 * 답변(댓글)은 이 슬라이스 범위 밖 - 필요해지면 같은 패키지에 Answer 엔티티로 붙인다.
 *
 * 인덱스를 직접 명시하는 이유: PostgreSQL은 MySQL과 달리 FK에 인덱스를 자동 생성하지 않는다.
 * 목록이 "분반 안에서 최신순"이라 (cohort_id, created_at) 복합 인덱스 하나로 조회·정렬을 모두 받는다.
 */
@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_questions_cohort_created", columnList = "cohort_id, created_at"),
        @Index(name = "idx_questions_author", columnList = "author_id")
})
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cohort_id", nullable = false)
    private Cohort cohort;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Question() {
        // JPA 스펙이 요구하는 기본 생성자
    }

    private Question(Cohort cohort, User author, String title, String content) {
        this.cohort = cohort;
        this.author = author;
        this.title = title;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public static Question create(Cohort cohort, User author, String title, String content) {
        return new Question(cohort, author, title, content);
    }

    /** PUT 전체 교체 - 제목·내용을 한 번에 바꾼다. 작성자·분반은 바뀌지 않는다 */
    public void update(String title, String content) {
        this.title = title;
        this.content = content;
    }

    /** 작성자 본인인가 - 수정 권한 판정용. 프록시의 getId()는 DB를 치지 않는다 */
    public boolean isWrittenBy(User user) {
        return author.getId().equals(user.getId());
    }

    public Long getId() { return id; }
    public Cohort getCohort() { return cohort; }
    public User getAuthor() { return author; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
