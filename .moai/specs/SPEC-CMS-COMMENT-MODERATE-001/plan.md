# SPEC-CMS-COMMENT-MODERATE-001 구현 계획

## Phase 1: Backend — 데이터 계층 (MyBatis)

- [ ] `BbsCommentMapper.java` — `listForAdmin`, `countForAdmin`, `updateStatus`, `adminDeleteComment` 메서드 추가
- [ ] `BbsCommentMapper.xml` — 대응 SQL 작성 (JOIN bbs_post, bbs_master)
- [ ] `CommentAdminSummary.java` DTO record 생성
- [ ] `CommentAdminListRequest.java` 요청 파라미터 record 생성

## Phase 2: Backend — 서비스/컨트롤러 계층

- [ ] `CommentAdminService.java` 인터페이스 생성
- [ ] `CommentAdminServiceImpl.java` 구현
- [ ] `CommentAdminController.java` REST 컨트롤러 생성

## Phase 3: Backend — 테스트

- [ ] `CommentAdminControllerIT.java` 통합 테스트 작성
  - AC-CMTM-001~005 커버

## Phase 4: Frontend

- [ ] `CommentManagementView.vue` 뷰 생성
- [ ] 라우트 등록 (`router/index.ts`)
- [ ] i18n 키 추가 (`ko.json`)

## 의존성

Phase 1 → Phase 2 → Phase 3 (순차)
Phase 4 (Phase 1~2와 병렬 가능)
