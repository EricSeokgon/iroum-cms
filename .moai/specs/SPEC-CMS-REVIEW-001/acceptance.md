# SPEC-CMS-REVIEW-001 인수 기준 (acceptance.md)

Given/When/Then 형식의 인수 기준. 각 기준은 관찰 가능한 증거(HTTP 상태, DB 상태, 응답 본문)로 검증한다.

## AC-REV-001: 인증 사용자 리뷰 작성

- **Given** 인증된 사용자가 존재하고 게시물 `postId`가 존재하며 리뷰 기능이 활성화된 게시판일 때
- **When** `POST /api/v1/posts/{postId}/reviews`에 `{ rating: 3, content: "좋은 글입니다" }`로 요청하면
- **Then** 201 Created를 반환하고 응답 본문에 생성된 리뷰 `id`, `rating=3`, `status="VISIBLE"`를 포함한다.
- 검증: `bbs_post_review`에 status=VISIBLE 레코드 1건 생성, `author_id`/`ip_address` 기록.
- 대응: REQ-REV-001, REQ-REV-008

## AC-REV-002: 동일 사용자 동일 게시물 다중 리뷰 허용

- **Given** AC-REV-001에서 동일 사용자가 동일 `postId`에 이미 리뷰 1건을 작성한 상태일 때
- **When** 동일 사용자가 동일 `postId`에 두 번째 리뷰 `{ rating: 5, content: "다시 읽어도 좋네요" }`로 `POST` 하면
- **Then** 201 Created를 반환하고 (유니크 제약 위반 없이) 두 번째 리뷰가 생성된다.
- 검증: 해당 (post_id, author_id) 조합 리뷰 2건 존재.
- 대응: REQ-REV-002

## AC-REV-003: 평균 별점 게시물 집계 반영

- **Given** 게시물 `postId`에 VISIBLE 리뷰가 rating 3 한 건만 있을 때
- **When** 동일 게시물에 rating 5 리뷰가 추가 생성되면
- **Then** `bbs_post.review_count = 2`, `bbs_post.average_rating = 4.0`으로 갱신된다.
- 검증: 게시물 조회 응답의 `averageRating=4.0`, `reviewCount=2` (VISIBLE 모수).
- 대응: REQ-REV-003, REQ-REV-009

## AC-REV-004: 비인증 사용자 작성 차단

- **Given** 인증 토큰이 없는(미로그인) 요청자일 때
- **When** `POST /api/v1/posts/{postId}/reviews`로 리뷰 작성을 시도하면
- **Then** 401 Unauthorized를 반환하고 리뷰가 생성되지 않는다.
- 검증: `bbs_post_review` 신규 레코드 없음. (단, `GET /reviews` 조회는 200 허용.)
- 대응: REQ-REV-007

## AC-REV-005: 관리자 전체 리뷰 목록 조회

- **Given** ADMIN 또는 MANAGER 역할(또는 REVIEW:READ 권한) 사용자와 다수 리뷰가 존재할 때
- **When** `GET /api/v1/admin/reviews?page=0&size=20` 요청하면
- **Then** 200 OK와 함께 VISIBLE/HIDDEN을 포함한 전체 리뷰의 페이지네이션 응답(총 건수, 페이지 메타)을 반환한다.
- 검증: 응답에 status 필터링 가능, HIDDEN 리뷰도 관리자 목록에 포함.
- 대응: REQ-REV-004, REQ-REV-011

## AC-REV-006: 관리자 리뷰 삭제 멱등성

- **Given** ADMIN 사용자와 VISIBLE 리뷰 `id`가 존재할 때
- **When** `DELETE /api/v1/admin/reviews/{id}`를 호출한 뒤 **동일 `id`로 재차 `DELETE`** 하면
- **Then** 두 호출 모두 204 No Content를 반환하고, 리뷰 status는 DELETED로 유지되며 복구되지 않는다.
- 검증: `status=DELETED`, `deleted_at` 설정, 재삭제 시 상태 불변(비가역), 집계에서 제외.
- 대응: REQ-REV-005, REQ-REV-006, REQ-REV-010

## AC-REV-007: 일반 사용자 관리자 API 접근 차단

- **Given** USER 역할(관리 권한 없음) 인증 사용자일 때
- **When** `GET /api/v1/admin/reviews`에 접근하면
- **Then** 403 Forbidden을 반환한다.
- 검증: 응답 403, 리뷰 목록 미노출. (PATCH `/hide`, DELETE도 동일하게 403.)
- 대응: REQ-REV-012

## AC-REV-008: 관리자 리뷰 숨김 집계 제외

- **Given** ADMIN 사용자와 게시물 `postId`에 VISIBLE 리뷰 2건(rating 4, 2 → average 3.0)이 있을 때
- **When** rating 2 리뷰를 `PATCH /api/v1/admin/reviews/{id}/hide`로 숨김 처리하면
- **Then** 200을 반환하고 해당 게시물 `average_rating=4.0`, `review_count=1`로 재집계된다(HIDDEN 제외).
- 검증: HIDDEN 리뷰는 공개 `GET /reviews`에서 미노출, 관리자 목록에서는 노출.
- 대응: REQ-REV-005, REQ-REV-010, REQ-REV-011

## 별점 범위 검증 (Edge Case)

- **Given** 인증 사용자일 때
- **When** `rating: 0` 또는 `rating: 6` 등 1~5 범위 밖 값으로 `POST` 하면
- **Then** 400 Bad Request를 반환하고 리뷰가 생성되지 않는다 (서비스 검증 + DB CHECK).
- 대응: REQ-REV-008

## Definition of Done

- [ ] V55 마이그레이션 적용 성공(테이블/컬럼/권한/메뉴 시드)
- [ ] AC-REV-001~008 + 별점 범위 edge case 전부 통과
- [ ] `ReviewServiceTest`, `ReviewAdminControllerIT` GREEN
- [ ] 인가 매트릭스(401/403/200) 검증 완료
- [ ] 평균 별점 집계가 VISIBLE 모수로 정확(hide/delete 후 재집계)
- [ ] 관리자 메뉴/라우트 권한 가드(REVIEW:READ) 동작
- [ ] DELETED 비가역 보장(복구 불가)
