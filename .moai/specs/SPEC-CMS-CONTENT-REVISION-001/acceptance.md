# SPEC-CMS-CONTENT-REVISION-001 — 인수 기준 (acceptance.md)

Given-When-Then 형식. REQ당 3~4개, 총 24개. 게시물(Post)·페이지(Page) 양 도메인 커버.

---

## REQ-REV-001 — Revision 자동 생성

### AC-001-1 (게시물 저장 시 스냅샷 적재)
- **Given** 관리자가 기존 게시물(version=3)을 편집하고
- **When** 저장 요청을 보내면
- **Then** 저장 직전 본문이 `bbs_post_history`에 새 version으로 적재되고 `bbs_post.version`이 4로 증가한다.

### AC-001-2 (페이지 저장 시 스냅샷 적재)
- **Given** 관리자가 기존 페이지(current_version=2)를 편집하고
- **When** 저장 요청을 보내면
- **Then** 저장 직전 스냅샷이 `page_history`(JSONB)에 새 version으로 적재되고 `page.current_version`이 3으로 증가한다.

### AC-001-3 (기존 적재 경로 보존)
- **Given** 페이지 수정 시 slug가 변경되었고
- **When** 저장이 완료되면
- **Then** 기존 동작(seo_redirect 자동 INSERT, 이력 누적)이 그대로 유지되며 본 SPEC이 이를 깨뜨리지 않는다.

### AC-001-4 (낙관락 version과 history version 규칙 일관)
- **Given** 게시물을 연속 2회 저장하고
- **When** 이력과 현재 행을 조회하면
- **Then** `bbs_post.version`과 최신 `bbs_post_history.version`이 정의된 단일 규칙(plan M1 확정)에 따라 일관된 값을 갖는다.

---

## REQ-REV-002 — Revision 목록 조회

### AC-002-1 (게시물 목록 version DESC + 페이징)
- **Given** 게시물에 5개 revision이 있고
- **When** `GET /api/v1/board/posts/{postId}/history?page=0&size=3`
- **Then** version 내림차순 3개가 `PageResponse`(요소+페이징 메타)로 반환된다.

### AC-002-2 (페이지 목록 version DESC)
- **Given** 페이지에 revision이 있고
- **When** `GET /api/v1/content/pages/{id}/history`
- **Then** version 내림차순 목록이 반환된다(기존 `getPageHistory` 동작 유지).

### AC-002-3 (editor 표시명 포함, 삭제 사용자 안전)
- **Given** 한 revision의 editor가 삭제되어 `edited_by`가 NULL이고
- **When** 목록을 조회하면
- **Then** editorName이 null(또는 합의된 기본값)로 반환되고 오류 없이 항목이 포함된다.

### AC-002-4 (변경 요약 포함)
- **Given** 저장 시 edit_reason/change_summary가 기록되었고
- **When** 목록을 조회하면
- **Then** 각 항목에 해당 변경 요약 텍스트가 포함된다.

---

## REQ-REV-003 — Diff 비교

### AC-003-1 (게시물 본문 라인 diff)
- **Given** 게시물 version 2와 3의 content_html이 일부 라인만 다르고
- **When** `GET /api/v1/board/posts/{postId}/history/diff?from=2&to=3`
- **Then** 동일 라인은 EQUAL, 추가 라인은 INSERT, 삭제 라인은 DELETE로 정확히 표기된 라인 목록이 반환된다.

### AC-003-2 (제목 diff)
- **Given** 두 version의 title이 다르고
- **When** diff를 조회하면
- **Then** title 필드 diff에 변경이 반영된다.

### AC-003-3 (페이지 slug diff — 페이지 전용)
- **Given** 페이지 두 version의 slug가 다르고
- **When** `GET /api/v1/content/pages/{id}/history/diff?from&to`
- **Then** slug 필드 diff가 포함된다(게시물 응답에는 slug diff가 없다).

### AC-003-4 (페이지 JSONB snapshot 평탄화 안정성)
- **Given** 페이지 콘텐츠 블록 순서가 동일하고 본문만 한 블록 바뀌었으며
- **When** diff를 조회하면
- **Then** 블록 순서/키 순서 차이로 인한 노이즈 없이 실제 변경 블록만 diff로 표시된다.

---

## REQ-REV-004 — Revision 롤백

### AC-004-1 (게시물 롤백 내용 복원)
- **Given** 게시물 현재 version=5, 과거 version=2로 롤백을 요청하고
- **When** `POST /api/v1/board/posts/{postId}/rollback/2`
- **Then** 게시물 title/content_html이 version=2 스냅샷으로 복원된다.

### AC-004-2 (롤백 불변성 — 새 revision 적재)
- **Given** AC-004-1 롤백이 수행되면
- **When** 이력을 조회하면
- **Then** 롤백 직전 상태(version=5)가 새 history version(=6)으로 보존되고 과거 version(1~5)은 파괴되지 않는다.

### AC-004-3 (페이지 롤백 재사용)
- **Given** 페이지를 과거 version으로 롤백하고
- **When** `POST /api/v1/content/pages/{id}/rollback/{version}`
- **Then** 기존 `PageService.rollbackPage` 동작(내용 복원 + status DRAFT 강제 + 새 revision)이 그대로 수행된다.

### AC-004-4 (존재하지 않는 version 롤백 404)
- **Given** 존재하지 않는 version으로 롤백을 요청하면
- **When** 롤백 엔드포인트를 호출하면
- **Then** 404가 반환되고 게시물/페이지 내용은 변경되지 않는다.

---

## REQ-REV-005 — 동시 편집 충돌 감지 (낙관적 잠금)

### AC-005-1 (게시물 충돌 409)
- **Given** 사용자 A와 B가 모두 게시물 version=3을 로드했고 A가 먼저 저장해 version=4가 되었으며
- **When** B가 expectedVersion=3으로 저장을 시도하면
- **Then** `409 Conflict`와 `{ code:"REVISION_CONFLICT", currentVersion:4 }`가 반환되고 A의 변경이 덮어써지지 않는다.

### AC-005-2 (페이지 충돌 409)
- **Given** 두 사용자가 페이지 current_version=2를 로드했고 한 명이 먼저 저장해 3이 되었으며
- **When** 다른 사용자가 expectedVersion=2로 저장하면
- **Then** 409와 서버 현재 version(3)이 반환되고 덮어쓰기가 발생하지 않는다.

### AC-005-3 (정상 저장은 통과)
- **Given** 사용자가 최신 version=4를 로드했고
- **When** expectedVersion=4로 저장하면
- **Then** 저장이 성공하고 version이 5로 증가한다.

### AC-005-4 (expectedVersion 누락 처리)
- **Given** 수정 요청에 expectedVersion이 누락되었고
- **When** 저장을 시도하면
- **Then** 정의된 정책(400 검증 오류 또는 강제 충돌 검사)에 따라 일관되게 처리되며 무조건 덮어쓰기는 일어나지 않는다.

---

## REQ-REV-006 — Revision 보존 정책

### AC-006-1 (N 초과 시 최오래된 삭제)
- **Given** `content.revision.maxPerEntity`=50이고 한 게시물에 50개 revision이 있으며
- **When** 새 저장으로 51번째가 적재되면
- **Then** 가장 오래된 version 1개가 삭제되어 총 50개가 유지된다.

### AC-006-2 (페이지에도 동일 적용)
- **Given** 페이지 revision이 N에 도달했고
- **When** 추가 저장이 발생하면
- **Then** 페이지 `page_history`에서도 가장 오래된 version부터 정리되어 N 이하로 유지된다.

### AC-006-3 (best-effort — 정리 실패가 저장을 막지 않음)
- **Given** retention 정리 단계가 실패하더라도
- **When** 저장 요청이 처리되면
- **Then** 본문 저장과 revision 적재는 성공으로 완료되고 정리 실패는 로깅된다.

### AC-006-4 (설정값 변경 반영)
- **Given** 관리자가 `system_setting`에서 N을 10으로 변경하고
- **When** 이후 저장이 발생하면
- **Then** 새 임계값 10 기준으로 정리가 적용된다.

---

## REQ-REV-007 — 프론트엔드 Revision UI

### AC-007-1 (게시물 편집 화면 Revision 패널)
- **Given** 관리자가 `PostFormView.vue`/`PostDetailView.vue`에서 Revision 패널을 열고
- **When** 패널이 로드되면
- **Then** version·수정자·일시·변경요약 목록이 표시된다.

### AC-007-2 (두 항목 선택 → diff 뷰어)
- **Given** 관리자가 목록에서 두 revision을 선택하고
- **When** diff 보기를 누르면
- **Then** `DiffViewer`가 추가/삭제/동일 라인을 색상으로 구분해 표시한다.

### AC-007-3 (롤백 액션)
- **Given** 관리자가 특정 revision을 선택하고
- **When** 롤백을 실행하면
- **Then** 해당 버전으로 복원되고 목록에 새 revision이 추가 표시된다.

### AC-007-4 (409 충돌 안내)
- **Given** 다른 사용자가 먼저 저장해 충돌이 발생했고
- **When** 현재 사용자가 저장을 시도해 409를 받으면
- **Then** 충돌 안내 모달이 표시되고 서버 최신본과 비교/재작업 경로가 제시된다(덮어쓰기 강행 버튼 없음).

---

## Definition of Done

- [ ] REQ-REV-001~007 전 항목 구현 및 위 24개 AC 통과
- [ ] V54 마이그레이션 적용(main 최신 재확인 후 번호 확정), `bbs_post.version` + retention 기본값
- [ ] 게시물·페이지 도메인 분리 유지(통합 테이블 미신설)
- [ ] 낙관락 동시성 테스트(409), diff 정확성 테스트, 롤백 불변성 테스트, retention 경계 테스트
- [ ] 기존 `updatePage`/POST-HISTORY read API 회귀 없음(특성화 테스트)
- [ ] 공유 Revision 컴포넌트가 게시물·페이지 양쪽에서 동작, ko/en i18n 완비
- [ ] TRUST 5 게이트 통과, 백엔드 85%+ 커버리지

## 품질 게이트 기준

- 낙관락: 동시 저장 시 정확히 한 요청만 성공, 나머지 409.
- diff: 라인 단위 추가/삭제/동일이 실제 변경과 일치(노이즈 0).
- 롤백: 어떤 과거 version도 파괴되지 않음(불변 보장).
- retention: 적재는 항상 성공, 초과분만 정리.
- 보안: revision/diff/롤백 모두 관리자 권한 필수, 비인증 401/403.
