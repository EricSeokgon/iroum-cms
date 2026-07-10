# SPEC-CMS-REVIEW-001 Compact

게시물(BbsPost) 별점(1-5) + 리뷰 텍스트 시스템. 다중 리뷰 허용, 평균 별점 집계, 관리자 중앙 관리. 신규 테이블 `bbs_post_review` 분리, V56 마이그레이션.

## Requirements (REQ-REV-*)

### Ubiquitous
- REQ-REV-001: 인증 사용자는 게시물에 별점(1-5 정수) + 리뷰 텍스트를 작성할 수 있다.
- REQ-REV-002: 동일 사용자가 동일 게시물에 여러 리뷰 작성 가능 (다중 허용, 유니크 제약 없음).
- REQ-REV-003: 게시물 단위 VISIBLE 리뷰의 평균 별점(average_rating) + 리뷰 수(review_count) 집계.
- REQ-REV-004: 관리자(ADMIN/MANAGER, REVIEW:READ)는 전체 리뷰 목록을 페이지네이션 조회.
- REQ-REV-008: 별점은 1~5 정수만 저장 (DB CHECK + 서비스 검증).

### Event-Driven
- REQ-REV-009: When 리뷰 신규 생성(VISIBLE) → 게시물 review_count/average_rating 재집계.
- REQ-REV-010: When 관리자 숨김/삭제 → 집계에서 제외하고 재집계.

### State-Driven
- REQ-REV-005: While VISIBLE → 공개 조회/집계 포함. 관리자는 숨김(HIDDEN)/삭제(DELETED) 가능.
- REQ-REV-011: While HIDDEN → 공개 조회/집계 제외, 관리자 목록은 표시.

### Unwanted
- REQ-REV-006: If DELETED → 복구/재활성화 불가 (비가역).
- REQ-REV-007: If 비인증 → 작성(POST) 차단 401. 조회는 허용.
- REQ-REV-012: If 관리 권한 없는 일반 사용자 → 관리자 API 403.

### Optional
- REQ-REV-013: Where 게시판 리뷰 기능 활성 → 작성 UI/엔드포인트 노출 (비활성 시 graceful disable).

## Acceptance Criteria (Given/When/Then)

- AC-REV-001: 인증 사용자 별점3+텍스트 POST → 201, status=VISIBLE.
- AC-REV-002: 동일 사용자 동일 게시물 2번째 리뷰 POST → 201 (다중 허용).
- AC-REV-003: rating 3 존재 + rating 5 추가 → review_count=2, average_rating=4.0.
- AC-REV-004: 비인증 POST → 401, 레코드 미생성 (GET 조회는 200).
- AC-REV-005: 관리자 GET /admin/reviews → 200, VISIBLE+HIDDEN 포함 페이지네이션.
- AC-REV-006: 관리자 DELETE 후 동일 id 재삭제 → 모두 204, DELETED 유지 (멱등/비가역).
- AC-REV-007: USER 역할 GET /admin/reviews → 403.
- AC-REV-008: 관리자 hide(rating2) → average 4.0/count 1 재집계 (HIDDEN 제외).
- Edge: rating 0 또는 6 POST → 400.

## Files to Modify

- [NEW] V56__review_system_rbac.sql (DDL + permissions + admin_menu seed)
- [NEW] BbsPostReview.java (entity), BbsPostReviewMapper.java + .xml
- [NEW] ReviewService/Impl, ReviewAdminService/Impl
- [NEW] ReviewController, ReviewAdminController, Review* DTO
- [MODIFY] BbsPost.java (+reviewCount int, +averageRating BigDecimal), BbsPostMapper.xml
- [NEW] ReviewManagementView.vue, PostReviewSection.vue
- [MODIFY] router/index.ts (+/admin/reviews, meta.permissions REVIEW:READ)
- [NEW] ReviewServiceTest.java, ReviewAdminControllerIT.java, ReviewManagementView.spec.ts

패키지 루트: kr.co.ircp.cms.domain.board. API: POST/GET /api/v1/posts/{postId}/reviews, GET/PATCH/DELETE /api/v1/admin/reviews.

## Exclusions (What NOT to Build)

- EX1: QnA/댓글 대상 리뷰 (게시물 BbsPost 한정).
- EX2: 리뷰 수정(편집) 기능.
- EX3: 리뷰 답글/스레드(대댓글).
- EX4: 사용자 좋아요/신고 워크플로 (관리자 직접 모더레이션만).
- EX5: 삭제된 리뷰 복구.
- EX6: 별점 집계 실시간 캐시/MV.
