---
id: SPEC-CMS-REVIEW-001
version: "0.1.0"
status: Planned
created: 2026-06-22
updated: 2026-06-22
author: ircp
priority: Medium
issue_number: 0
---

# SPEC-CMS-REVIEW-001 게시물 별점 리뷰 시스템 (Post Star Rating & Review System)

## HISTORY

- 2026-06-22 (v0.1.0): 초안 작성. 게시물(BbsPost) 대상 별점(1-5) + 리뷰 텍스트 시스템. 다중 리뷰 허용, 평균 별점 집계, 관리자 중앙 관리(목록/숨김/삭제). `bbs_post_review` 신규 테이블 분리. V55 마이그레이션.

---

## 1. Overview (개요)

게시물(BbsPost)에 대해 인증된 사용자가 별점(1~5점)과 리뷰 텍스트를 작성할 수 있는 평가 시스템을 구축한다. 동일 사용자가 동일 게시물에 여러 리뷰를 작성하는 다중 리뷰를 허용하며, 리뷰의 평균 별점은 게시물 단위로 집계되어 노출된다. 관리자(ADMIN/MANAGER)는 전체 리뷰를 중앙에서 조회/숨김/삭제로 관리한다.

핵심 설계 결정: 기존 `bbs_comment`(스레드형 댓글)는 평점 필드가 없고 용도가 다르므로, 평점 가능 리뷰를 위한 **별도 테이블 `bbs_post_review`**를 신설한다. 기존 `SPEC-CMS-COMMENT-MODERATE-001`(댓글 관리: 목록/숨김/삭제)에서 확립된 관리자 모더레이션 패턴과 RBAC 권한 매핑 패턴을 재사용하되, 리뷰는 독립 리소스(`REVIEW:*` 권한)로 분리한다.

## 2. Goals (목표)

- G1: 인증 사용자가 게시물에 별점 + 리뷰 텍스트를 작성하는 공개 API 제공
- G2: 동일 사용자의 동일 게시물 다중 리뷰 허용
- G3: 게시물 단위 평균 별점(`average_rating`)과 리뷰 수(`review_count`) 집계 및 노출
- G4: 관리자(ADMIN/MANAGER)의 전체 리뷰 중앙 관리(목록 조회 / 숨김 / 삭제)
- G5: 비인증 사용자의 리뷰 조회 허용, 작성 차단
- G6: RBAC 권한(`REVIEW:READ`, `REVIEW:WRITE`, `REVIEW:DELETE`) 및 관리자 메뉴(`admin_menu`) 시드를 V55 단일 마이그레이션으로 통합

## 3. Non-Goals / Exclusions (제외 범위)

[HARD] 본 SPEC에서 구현하지 않는 항목 (What NOT to Build):

- EX1: **QnA(qna_post) / 댓글(bbs_comment) 대상 리뷰** — 평가 대상은 게시물(BbsPost)로 한정. 다른 콘텐츠 타입 확장은 별도 SPEC.
- EX2: **리뷰 수정(편집) 기능** — 본 SPEC은 작성(생성)·조회·관리(숨김/삭제)만 다룬다. 작성자 본인 수정은 다중 리뷰 허용 정책으로 갈음하며 별도 SPEC으로 분리.
- EX3: **리뷰에 대한 답글/스레드(대댓글)** — 리뷰는 단일 레벨(flat). 스레드형은 기존 `bbs_comment` 영역.
- EX4: **리뷰 좋아요/신고(사용자 신고 워크플로)** — 사용자 측 신고 기능은 본 SPEC 제외. 관리자 직접 모더레이션만 제공.
- EX5: **삭제된 리뷰 복구(restore)** — DELETED 상태는 비가역(REQ-REV-006).
- EX6: **별점 집계의 실시간 캐시/머티리얼라이즈드 뷰** — 집계는 서비스 계층 갱신으로 수행하며 별도 캐시 인프라는 도입하지 않는다.

## 4. Requirements (EARS Format)

### 4.1 Ubiquitous (시스템 상시 규칙)

- **REQ-REV-001**: The system **shall** allow 인증된 사용자가 게시물에 별점(1~5 정수) + 리뷰 텍스트를 작성하도록 한다.
- **REQ-REV-002**: The system **shall** allow 동일 사용자가 동일 게시물에 여러 리뷰를 작성하도록 한다 (다중 리뷰 허용, 유니크 제약 없음).
- **REQ-REV-003**: The system **shall** 게시물 단위로 가시(VISIBLE) 상태 리뷰의 평균 별점(`average_rating`)과 리뷰 수(`review_count`)를 집계하여 게시물에 반영한다.
- **REQ-REV-004**: The system **shall** allow 관리자(ADMIN 또는 MANAGER 역할, 또는 `REVIEW:READ` 권한 보유자)가 전체 리뷰 목록을 페이지네이션으로 조회하도록 한다.
- **REQ-REV-008**: The system **shall** 별점 값을 1 이상 5 이하의 정수로만 저장한다 (DB CHECK 제약 + 서비스 검증).

### 4.2 Event-Driven (이벤트 기반)

- **REQ-REV-009**: **When** 리뷰가 신규 생성(VISIBLE)되면, the system **shall** 해당 게시물의 `review_count`와 `average_rating`을 재집계한다.
- **REQ-REV-010**: **When** 관리자가 리뷰를 숨김(HIDDEN) 또는 삭제(DELETED)하면, the system **shall** 해당 게시물의 집계(`review_count`, `average_rating`)에서 해당 리뷰를 제외하고 재집계한다.

### 4.3 State-Driven (상태 기반)

- **REQ-REV-005**: **While** 리뷰가 VISIBLE 상태인 동안, the system **shall** 해당 리뷰를 공개 조회 결과와 평균 별점 집계에 포함한다. 관리자는 부적절한 리뷰를 숨김(HIDDEN) 처리하거나 삭제(DELETED)할 수 있다.
- **REQ-REV-011**: **While** 리뷰가 HIDDEN 상태인 동안, the system **shall** 해당 리뷰를 공개 조회 결과와 평균 별점 집계에서 제외하되 관리자 목록에서는 표시한다.

### 4.4 Unwanted Behavior (금지 동작)

- **REQ-REV-006**: **If** 리뷰가 DELETED 상태이면, **then** the system **shall not** 해당 리뷰를 복구하거나 재활성화한다 (삭제는 비가역).
- **REQ-REV-007**: **If** 요청자가 비인증(미로그인) 사용자이면, **then** the system **shall not** 리뷰 작성(POST)을 허용하고 401을 반환한다. 조회는 허용한다.
- **REQ-REV-012**: **If** 요청자가 관리 권한(ADMIN/MANAGER 또는 `REVIEW:READ`)이 없는 일반 사용자이면, **then** the system **shall not** 관리자 리뷰 API 접근을 허용하고 403을 반환한다.

### 4.5 Optional (선택 기능)

- **REQ-REV-013**: **Where** 게시판(Bbs) 설정에서 리뷰 기능이 활성화된 경우, the system **shall** 해당 게시판 게시물에 리뷰 작성 UI/엔드포인트를 노출한다. (비활성 게시판은 graceful disable — 기능 비활성 시 작성 차단, 기존 데이터 영향 없음.)

## 5. Technical Approach (기술 접근)

### 5.1 신규 테이블: `bbs_post_review`

| 컬럼 | 타입 | 제약 |
|------|------|------|
| `id` | BIGINT | PK, generated |
| `post_id` | BIGINT | NOT NULL, REFERENCES `bbs_post(id)` ON DELETE CASCADE |
| `author_id` | BIGINT | REFERENCES `users(id)` ON DELETE SET NULL (작성자 탈퇴 시 익명화) |
| `rating` | SMALLINT | NOT NULL, CHECK (`rating` BETWEEN 1 AND 5) |
| `content` | TEXT | NULL 허용 (별점만 작성 가능) |
| `status` | VARCHAR(20) | NOT NULL DEFAULT 'VISIBLE', CHECK IN ('VISIBLE','HIDDEN','DELETED') |
| `ip_address` | INET | NULL 허용 (작성 IP 기록) |
| `created_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| `updated_at` | TIMESTAMPTZ | NOT NULL DEFAULT now() |
| `deleted_at` | TIMESTAMPTZ | NULL 허용 (DELETED 전이 시각) |

인덱스: `(post_id, status)` 복합 인덱스(공개 조회/집계), `(author_id)` 인덱스, `(created_at DESC)` 관리자 목록 정렬용.

다중 리뷰 허용 → `(post_id, author_id)` 유니크 제약 **두지 않음**.

### 5.2 `bbs_post` 변경 (additive)

- `review_count INT NOT NULL DEFAULT 0` 추가
- `average_rating DECIMAL(3,1) NOT NULL DEFAULT 0.0` 추가 (0.0 ~ 5.0)

집계는 서비스 계층(`ReviewServiceImpl`)에서 VISIBLE 리뷰 기준으로 갱신한다. 기존 `likeCount`/`commentCount` 집계 패턴과 동일한 방식. (트리거는 도입하지 않고 서비스 단일 책임으로 일관성 유지 — EX6.)

### 5.3 RBAC 권한 시드

`permissions` 테이블에 신규 추가 (code, resource, action):
- `REVIEW:READ` (resource=REVIEW, action=READ)
- `REVIEW:WRITE` (resource=REVIEW, action=WRITE)
- `REVIEW:DELETE` (resource=REVIEW, action=DELETE)

`role_permissions`로 ADMIN, MANAGER 역할에 3개 권한 전부 매핑. (기존 V50~V54 권한 시드 패턴 재사용.)

### 5.4 관리자 메뉴

`admin_menu`에 "리뷰 관리" 항목 시드 (부모: 콘텐츠/게시판 관리 그룹 하위, 경로: `/admin/reviews`, 필요 권한: `REVIEW:READ`).

### 5.5 API 표면

| 메서드 | 경로 | 권한 | 설명 |
|--------|------|------|------|
| POST | `/api/v1/posts/{postId}/reviews` | 인증 | 리뷰 작성 (REQ-REV-001) |
| GET | `/api/v1/posts/{postId}/reviews` | 공개 | 게시물 VISIBLE 리뷰 목록 |
| GET | `/api/v1/admin/reviews` | `REVIEW:READ` | 전체 리뷰 목록(페이지네이션, 상태 필터) |
| PATCH | `/api/v1/admin/reviews/{id}/hide` | `REVIEW:DELETE` | 리뷰 숨김(HIDDEN) |
| DELETE | `/api/v1/admin/reviews/{id}` | `REVIEW:DELETE` | 리뷰 삭제(DELETED, idempotent) |

기존 `/api/v1/posts/{id}/comments` 공개 API 패턴과 `@PreAuthorize` 가드 패턴을 따른다.

### 5.6 V55 마이그레이션 통합

`V55__review_system_rbac.sql` 단일 파일에 (1) `bbs_post_review` DDL, (2) `bbs_post` ALTER(additive), (3) `permissions` + `role_permissions` 시드, (4) `admin_menu` 시드를 포함한다.

> 주의: 미머지 Draft SPEC들이 V번호를 잠정 사용 중. run 직전 `backend/src/main/resources/db/migration/` 최신 버전 재확인 후 충돌 시 재번호.

## 6. Files to Modify (Delta Markers)

패키지 루트: `kr.co.ircp.cms.domain.board` (board 도메인 controller/service/repository/entity/dto 레이어링 준수).

- [NEW] `backend/src/main/resources/db/migration/V55__review_system_rbac.sql` — DDL + permissions + admin_menu seed
- [NEW] `.../domain/board/entity/BbsPostReview.java` — 엔티티 (Instant 타임스탬프, status String)
- [NEW] `.../domain/board/repository/BbsPostReviewMapper.java` — MyBatis 인터페이스
- [NEW] `backend/src/main/resources/mapper/board/BbsPostReviewMapper.xml` — SQL 쿼리
- [NEW] `.../domain/board/service/ReviewService.java` + `ReviewServiceImpl.java` — 공개 CRUD + 집계 갱신
- [NEW] `.../domain/board/service/ReviewAdminService.java` + `ReviewAdminServiceImpl.java` — 관리자 관리(목록/숨김/삭제)
- [NEW] `.../domain/board/controller/ReviewController.java` — 공개 API
- [NEW] `.../domain/board/controller/ReviewAdminController.java` — 관리자 API
- [NEW] `.../domain/board/dto/` — ReviewCreateRequest, ReviewResponse, AdminReviewResponse, ReviewPageResponse 등
- [MODIFY] `.../domain/board/entity/BbsPost.java` — `reviewCount`(int), `averageRating`(BigDecimal) 필드 추가
- [MODIFY] `backend/src/main/resources/mapper/board/BbsPostMapper.xml` — review 집계 컬럼 매핑/갱신 쿼리 추가
- [NEW] `frontend/src/views/admin/ReviewManagementView.vue` — 관리자 페이지 (el-table + el-pagination)
- [NEW] `frontend/src/components/post/PostReviewSection.vue` — 공개 리뷰 표시/작성 컴포넌트
- [MODIFY] `frontend/src/router/index.ts` — `/admin/reviews` 라우트 추가 (meta.permissions: REVIEW:READ)
- Tests: [NEW] `ReviewServiceTest.java`, `ReviewAdminControllerIT.java`, `ReviewManagementView.spec.ts`

## 7. MX Tag Plan (mx_plan)

- `ReviewServiceImpl.createReview` / `recalculateAggregate`: **@MX:ANCHOR** — controller(공개) + admin service 양쪽에서 호출되는 집계 불변 계약(high fan_in 예상). 집계 일관성이 핵심 불변식.
- `ReviewAdminController`: **@MX:NOTE** — 권한 가드(`REVIEW:READ`/`REVIEW:DELETE`) 동작과 idempotent delete 의도 설명.
- `BbsPostReviewMapper` soft-delete 쿼리: **@MX:WARN** (@MX:REASON: DELETED는 비가역, status 전이만 수행하고 물리 삭제 금지 — REQ-REV-006).

## 8. Constraints

- 스택: Spring Boot + egovframe + MyBatis (mapper XML), 프론트 Vue 3 + Composition API + Element Plus.
- 보안: `@PreAuthorize` 또는 권한 기반 가드. 작성 IP 기록(`ip_address`)은 PII 정책 준수(필요 시 마스킹 정책 SPEC 참조).
- 집계 정확성: 평균 별점은 VISIBLE 리뷰만 모수. HIDDEN/DELETED 제외(REQ-REV-010, 011).
