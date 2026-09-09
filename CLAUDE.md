# CLAUDE.md - ondal-BE

> 전체 기획·설계 맥락: [docs 레포](https://github.com/KNU-HAEDAL-Website-v3/ondal-docs)
> 이 파일: 백엔드 작업 시 필요한 최소 맥락 요약본

## 이 레포는

Ondal(부트캠프 과제 제출·관리 플랫폼 + 온라인 저지)의 API 서버. Spring Boot + Gradle + PostgreSQL.

## 핵심 설계 원칙

1. **OJ 문제 = 분반 없는 과제**
   - Assignment의 `cohort_id NULL` + `deadline NULL` + 자동채점 = OJ 문제
   - 과제 제출·OJ 풀이는 파이프라인 하나로 처리 - 코드 두 벌 작성 금지
2. **권한은 2층**: `User.global_role`(ADMIN/MEMBER) + `Enrollment.role`(OPERATOR/STUDENT)
   - 판정 순서: ① 로그인? → ② 전역 ADMIN이면 통과 → ③ 해당 분반 Enrollment 존재? → ④ role이 요구 수준 충족?
   - 판정은 공통 컴포넌트 하나로 구현해 모든 API에 적용 - 개별 API에서 권한 로직 별도 작성 금지
   - 상세: docs 레포 `docs/permissions.md`
3. **분반 생성·운영진 지정: 전역 ADMIN 전용** - 최초 관리자는 DB 수동 지정(부트스트랩)
4. **마감 판정: 서버 수신 시각 기준** - 저장은 UTC, 표시는 KST
5. **인증 금지선: 홈페이지 비밀번호를 Ondal이 직접 받는 구조 금지**
   - 연동 대상: 홈페이지 로그인(구글 OAuth + 자체 로그인, 구축 중)
   - 연동 확정 전까지: 스텁 인증으로 개발

## 코드 규칙

- 새 API: Cohort 수직 슬라이스(entity → repository → service → controller → DTO → 테스트) 패턴 그대로 준수 - 인터넷 튜토리얼 스타일 혼용 금지
- 고위험 영역(인증, 제출 판정, 파일 업로드, 권한 공통 처리): PM 담당 - 타인 수정 금지
- 제출 실패(401/400/422) 응답: 프론트가 작성 내용을 보존할 수 있도록 명확한 에러 코드 제공
- 모든 PR: "왜 이렇게 설계했는지"를 본문에 기재

## 문서 작성 규칙

- 모든 문서·PR 본문·이슈는 **개조식**으로 작성 (3개 레포 공통 - 상세: docs 레포 CONTRIBUTING.md)
  - 종결: 명사형·체언 종결("~함", "~됨", 명사구) - 서술형("~한다", "~입니다") 지양
  - 산문 문단 금지: 목록·표로 분해, 한 항목 = 한 정보
- 문단 부호 적극 사용: `-`, `1.`, `**강조**`, 표, `→`, `:`, `※`
- 키보드 밖 특수 부호 금지: em dash·en dash·말줄임표(한 글자)·절 기호(section sign) → `-`, `...`, `N절`

## 스코프 (P1)

- 포함: 로그인(스텁) · 분반 CRUD+운영진 지정 · 수강생 배정 · 과제 CRUD · 제출 · 마감 판정 · 미제출자 대시보드
- P1 제외(백로그): 출석부 / 수강신청 흐름 / Q&A 답변·알림 (질문 글 CRUD는 2026-09-09 P1 편입 - docs 결정 6, `qna` 슬라이스)
- P2: 자동채점(Judge0)
