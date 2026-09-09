package kr.haedal.ondal.qna.repository;

import kr.haedal.ondal.qna.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * 목록 - 최신순 (guide/design.md 4절: 목록 정렬 createdAt desc). 같은 시각이면 id desc 로 순서를 고정한다.
     * 응답에 작성자가 실리므로 author 를 fetch join 해 트랜잭션 안에서 LAZY 를 끝낸다 (WithXxx + @Query 규약).
     */
    @Query("select q from Question q join fetch q.author where q.cohort.id = :cohortId order by q.createdAt desc, q.id desc")
    List<Question> findAllByCohortIdWithAuthor(@Param("cohortId") Long cohortId);

    /** 하위 리소스 스코프 조회 규약 - 경로의 cohortId 와 함께 조회, 불일치·부재는 404 (guide/design.md 4절) */
    @Query("select q from Question q join fetch q.author where q.id = :id and q.cohort.id = :cohortId")
    Optional<Question> findByIdAndCohortIdWithAuthor(@Param("id") Long id, @Param("cohortId") Long cohortId);
}
