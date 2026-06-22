# SPEC-CMS-REVIEW-001 구현 계획 (plan.md)

## 기술 접근 요약

게시물(BbsPost) 대상 별점 리뷰 시스템. 신규 테이블 `bbs_post_review` 분리, `bbs_post` additive 집계 컬럼, RBAC 권한 + 관리자 메뉴 시드를 V55 단일 마이그레이션으로 통합. 공개 CRUD와 관리자 모더레이션을 별도 서비스/컨트롤러로 분리하고, 평균 별점 집계는 `ReviewServiceImpl` 단일 책임으로 관리한다.

기존 패턴 재사용:
- `SPEC-CMS-COMMENT-MODERATE-001`의 관리자 모더레이션(목록/숨김/삭제) 흐름
- V50~V54의 `permissions` / `role_permissions` / `admin_menu` 시드 방식
- `/api/v1/posts/{id}/comments` 공개 API 및 `@PreAuthorize` 가드 패턴

## Milestones (우선순위 기반, 시간 추정 없음)

### Phase A — Infrastructure (Priority High)

선행: 없음. 다른 모든 Phase의 기반.

- A1: V55 마이그레이션 작성 — `bbs_post_review` DDL (CHECK 1-5, FK, 인덱스), `bbs_post` ALTER(`review_count`, `average_rating`)
- A2: V55에 `permissions`(REVIEW:READ/WRITE/DELETE) + `role_permissions`(ADMIN, MANAGER) 시드
- A3: V55에 `admin_menu` "리뷰 관리"(`/admin/reviews`, 필요 권한 REVIEW:READ) 시드
- A4: `BbsPostReview.java` 엔티티 (Instant 타임스탬프, status String, rating int, BigDecimal 불사용)
- A5: `BbsPostReviewMapper.java` + `BbsPostReviewMapper.xml` (insert / selectByPost / selectAdminPage / updateStatus / 집계 쿼리)
- A6: `BbsPost.java`에 `reviewCount`(int), `averageRating`(BigDecimal) 필드 추가 + `BbsPostMapper.xml` 매핑 갱신

검증: V55 flyway migrate 성공, BbsPostReview CRUD 매퍼 단위 동작.

### Phase B — Backend Services & Controllers (Priority High)

선행: Phase A 완료.

- B1: `ReviewCreateRequest`, `ReviewResponse`, `AdminReviewResponse`, `ReviewPageResponse` DTO
- B2: `ReviewService` / `ReviewServiceImpl` — createReview(인증 검증, rating 1-5 검증, IP 기록), listByPost(VISIBLE만), recalculateAggregate(VISIBLE 모수 평균/카운트 갱신) [@MX:ANCHOR]
- B3: `ReviewAdminService` / `ReviewAdminServiceImpl` — listAll(페이지네이션 + status 필터), hide(VISIBLE→HIDDEN + 집계 제외), delete(→DELETED, idempotent, 비가역)
- B4: `ReviewController` — POST `/api/v1/posts/{postId}/reviews`(인증), GET `/api/v1/posts/{postId}/reviews`(공개)
- B5: `ReviewAdminController` — GET `/api/v1/admin/reviews`, PATCH `/{id}/hide`, DELETE `/{id}` (권한 가드 REVIEW:READ/DELETE) [@MX:NOTE]

검증: 집계 갱신 정확성(hide/delete 후 VISIBLE만 반영), idempotent delete.

### Phase C — Frontend (Priority Medium)

선행: Phase B API 계약 확정.

- C1: `PostReviewSection.vue` — 공개 게시물 상세에 별점 분포/평균/리뷰 목록 + 작성 폼(인증 시)
- C2: `ReviewManagementView.vue` — 관리자 페이지 (el-table 목록, status 필터, el-pagination, 숨김/삭제 액션)
- C3: `router/index.ts` — `/admin/reviews` 라우트 + `meta.permissions: ['REVIEW:READ']` 가드

검증: 권한 없는 사용자 라우트 가드 차단, 작성 폼 비인증 시 비노출.

### Phase D — Tests (Priority High)

선행: Phase B(필수), Phase C(프론트 스펙).

- D1: `ReviewServiceTest.java` — rating 검증, 다중 리뷰 허용, 집계 재계산
- D2: `ReviewAdminControllerIT.java` — 권한 매트릭스(ADMIN/MANAGER 200, USER 403, 비인증 401), idempotent delete 204
- D3: `ReviewManagementView.spec.ts` — 목록 렌더, 숨김/삭제 액션 호출
- D4: 인가 매트릭스 검증 — AC-REV-004/006/007 대응

검증: 전체 IT GREEN, 인가 매트릭스 통과.

## Task List (파일 배정)

| Task | 파일 | Phase |
|------|------|-------|
| V55 마이그레이션 | `backend/src/main/resources/db/migration/V55__review_system_rbac.sql` | A |
| 엔티티 | `.../board/entity/BbsPostReview.java` | A |
| 매퍼 IF/XML | `.../board/repository/BbsPostReviewMapper.java`, `mapper/board/BbsPostReviewMapper.xml` | A |
| BbsPost 확장 | `.../board/entity/BbsPost.java`, `mapper/board/BbsPostMapper.xml` | A |
| DTO | `.../board/dto/Review*.java` | B |
| 공개 서비스 | `.../board/service/ReviewService.java`, `ReviewServiceImpl.java` | B |
| 관리자 서비스 | `.../board/service/ReviewAdminService.java`, `ReviewAdminServiceImpl.java` | B |
| 공개 컨트롤러 | `.../board/controller/ReviewController.java` | B |
| 관리자 컨트롤러 | `.../board/controller/ReviewAdminController.java` | B |
| 공개 컴포넌트 | `frontend/src/components/post/PostReviewSection.vue` | C |
| 관리자 뷰 | `frontend/src/views/admin/ReviewManagementView.vue` | C |
| 라우트 | `frontend/src/router/index.ts` | C |
| 백엔드 테스트 | `ReviewServiceTest.java`, `ReviewAdminControllerIT.java` | D |
| 프론트 테스트 | `ReviewManagementView.spec.ts` | D |

## Risks (위험)

- R1: **V번호 충돌** — 미머지 Draft SPEC들이 V번호 잠정 사용. 완화: run 직전 최신 마이그레이션 재확인, 충돌 시 재번호.
- R2: **집계 일관성** — hide/delete와 동시 작성 시 평균 별점 race. 완화: 집계 갱신을 트랜잭션 내 재계산(SELECT 기반 full recompute)으로 수행, 증분 누적 금지.
- R3: **다중 리뷰 어뷰징** — 동일 사용자 다량 작성. 완화: 본 SPEC 범위 밖(EX4 신고 미포함). IP 기록으로 사후 관리자 모더레이션만 지원.
- R4: **작성 IP PII** — `ip_address` 저장. 완화: 기존 PII 마스킹/정책 SPEC 정렬, 노출 범위 관리자 한정.

## Delegation 권장

- 백엔드 구현: expert-backend (board 도메인 레이어링 + MyBatis)
- 프론트 구현: expert-frontend (Element Plus 관리자 테이블 + 라우트 가드)
- 인가 매트릭스 검증: 기존 SECURITY-AUTHZ-MATRIX 패턴 참조
