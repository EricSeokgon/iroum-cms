# SPEC-CMS-POINTS-001 구현 계획 (Plan)

## 기술 접근 요약

기존 인프라 재사용 우선 원칙을 따른다.

- **정책 저장**: 신규 정책 테이블을 만들지 않고 `system_setting` 키-값 저장소를 재사용한다.
- **원장 + 요약 분리**: append-only `user_point_ledger`(거래 단일 진실)와 비정규화 `user_point_summary`(총액 조회 성능)를 함께 운용한다.
- **좋아요 추적**: per-user 적립 판단을 위해 `bbs_post_like`를 신규 도입하되, 기존 `bbs_post.like_count` 카운터는 그대로 유지한다.
- **적립은 best-effort**: 원인 행위(게시글/댓글/좋아요)는 절대 포인트 적립 실패로 롤백되지 않는다.
- **단일 마이그레이션**: 모든 스키마/시드를 `V63__points_system.sql` 하나로 묶는다.

## 작업 분해 (Task Breakdown)

### T0: 사전 분석 및 정책 키 설계
- `system_setting` 타입 규약(STRING/INT/BOOL/JSON) 및 기존 키 네이밍 확인
- `permissions` 테이블 코드 패턴(`RESOURCE:ACTION`) 확인 → `POINTS:READ`, `POINTS:WRITE` 정의
- 정책 키 확정: `POINTS:ENABLED`(BOOL), `POINTS:POST_CREATED`(INT), `POINTS:COMMENT_CREATED`(INT), `POINTS:LIKE_GIVEN`(INT)
- 관련 요구사항: REQ-PNT-001, REQ-PNT-005

### T1: DB 마이그레이션 V63
- 파일: `backend/src/main/resources/db/migration/V63__points_system.sql`
- `user_point_ledger` 생성: id(PK), user_id(FK), event_type, points, ref_type, ref_id, created_at TIMESTAMPTZ DEFAULT NOW()
  - 인덱스: (user_id, created_at), (event_type)
- `user_point_summary` 생성: user_id(PK/FK), total_points DEFAULT 0, updated_at TIMESTAMPTZ DEFAULT NOW()
- `bbs_post_like` 생성: id(PK), user_id(FK), post_id(FK), created_at TIMESTAMPTZ DEFAULT NOW(), UNIQUE(user_id, post_id)
- system_setting seed: POINTS:ENABLED=false, POINTS:POST_CREATED=10, POINTS:COMMENT_CREATED=5, POINTS:LIKE_GIVEN=1 (기본값은 검토 후 확정)
- permissions seed: POINTS:READ, POINTS:WRITE
- 관련 요구사항: REQ-PNT-001 ~ REQ-PNT-007

### T2: 도메인 엔티티
- `domain/point/entity/UserPointLedger.java` — Long id, Long userId, String eventType, int points, String refType, Long refId, OffsetDateTime createdAt
- `domain/point/entity/UserPointSummary.java` — Long userId, long totalPoints, OffsetDateTime updatedAt
- `domain/board/entity/BbsPostLike.java` — Long id, Long userId, Long postId, OffsetDateTime createdAt
- 관련 요구사항: REQ-PNT-002, REQ-PNT-003, REQ-PNT-004

### T3: Mapper 인터페이스 + XML
- `point/mapper/UserPointLedgerMapper` — insert, selectBySearch(검색 DTO), countBySearch, selectByUserId(paging)
- `point/mapper/UserPointSummaryMapper` — upsertAddPoints(userId, delta), selectByUserId
- `board/mapper/BbsPostLikeMapper` — insert, existsByUserAndPost, deleteByUserAndPost, countByPost
- 공통 whereClause로 검색 조건(userId/eventType/기간) 처리
- 관련 요구사항: REQ-PNT-006

### T4: PointPolicyService
- `point/service/PointPolicyService.java` — SystemSetting 조회 → PointPolicy 값 객체 반환
  - `isEnabled()`, `pointsFor(eventType)` 제공
  - 키 부재 시 안전 기본값(disabled, 0점) 반환 (REQ-PNT-001 IF절)
  - 캐싱 없이 매 호출 조회하여 정책 변경 즉시 반영 (REQ-PNT-007)
- 관련 요구사항: REQ-PNT-001, REQ-PNT-007

### T5: UserPointService (적립 핵심)
- `point/service/UserPointService.java` (+ Impl, @Service, @RequiredArgsConstructor, @Transactional)
- `awardPoints(userId, eventType, refType, refId)`:
  1. `POINTS:ENABLED` false면 즉시 return (no-op) — REQ-PNT-007
  2. 정책 포인트 0이면 return
  3. ledger insert + summary upsert
  4. 전체를 try-catch로 감싸 예외 시 로그만 남기고 swallow → 원인 행위 롤백 방지 (REQ-PNT-008)
- `getSummary(userId)`, `getHistory(userId, paging)` 본인 조회 메서드
- 관련 요구사항: REQ-PNT-002, REQ-PNT-003, REQ-PNT-004, REQ-PNT-006, REQ-PNT-007, REQ-PNT-008

### T6: 게시글/댓글 적립 연동
- `PostServiceImpl.createPost()` 끝부분에 `userPointService.awardPoints(authorId, "POST_CREATED", "BBS_POST", postId)` 호출 추가
- `CommentServiceImpl.createComment()` 끝부분에 `userPointService.awardPoints(authorId, "COMMENT_CREATED", "BBS_COMMENT", commentId)` 호출 추가
- 동일 @Transactional 경계 내에서 호출하되 T5의 try-catch가 best-effort 보장
- 관련 요구사항: REQ-PNT-002, REQ-PNT-003, REQ-PNT-008

### T7: 좋아요 API + BbsPostLikeService
- `board/service/BbsPostLikeService.java` (+ Impl)
  - `like(userId, postId)`: existsByUserAndPost로 중복 확인 → 신규일 때만 insert + like_count 증가 + awardPoints("LIKE_GIVEN")
  - `unlike(userId, postId)`: 레코드 삭제 + like_count 감소, 포인트는 차감하지 않음 (REQ-PNT-004 IF절)
- 좋아요 엔드포인트 추가/연동 (POST `/api/.../posts/{postId}/like`, DELETE `/api/.../posts/{postId}/like`)
- 관련 요구사항: REQ-PNT-004

### T8: 관리자 API
- `point/controller/PointPolicyController.java`
  - GET 정책 조회, PUT/PATCH 정책 변경 — `POINTS:WRITE` 권한 가드 + `@AuditLog`
- `point/controller/PointLedgerController.java`
  - GET 관리자 내역 검색(userId/eventType/기간/paging) — `POINTS:READ`
  - GET 본인 내역/요약(인증 사용자 본인 한정) — 본인 식별자 강제
- DTO: PointPolicyDto, PointLedgerSearchDto, PointSummaryDto
- 관련 요구사항: REQ-PNT-005, REQ-PNT-006

### T9: 관리자 UI
- `PointPolicyAdminView.vue` — 이벤트 타입별 포인트 값 입력 + 시스템 ON/OFF 토글 (저장 시 즉시 반영)
- `PointLedgerAdminView.vue` — `EmailTemplateListView.vue` 패턴(필터 카드 + el-table + 페이지네이션), 사용자/이벤트/기간 검색
- `api/point.ts` — 정책 조회/변경, 내역 검색 클라이언트
- 라우터에 신규 라우트 + `meta.permissions` 등록
- 관련 요구사항: REQ-PNT-005, REQ-PNT-006, REQ-PNT-007

### T10: 사용자 향(User-Facing) UI
- `UserPointHistoryView.vue` 또는 프로필 영역 컴포넌트 — 본인 누적 총액 + 적립 내역 목록
- 본인 데이터만 표시(타인 조회 불가, REQ-PNT-006)
- 관련 요구사항: REQ-PNT-006

## 마일스톤 (우선순위 기반)

- **Milestone 1 (High)**: T0, T1, T2, T3, T4, T5 — 스키마 + 적립 코어. 이 단계 완료 시 포인트 적립의 기술적 토대 확보.
- **Milestone 2 (High)**: T6, T7 — 게시글/댓글/좋아요 실제 적립 연동. 사용자 활동이 포인트로 이어짐.
- **Milestone 3 (Medium)**: T8, T9 — 관리자 정책/내역 관리.
- **Milestone 4 (Medium)**: T10 — 사용자 향 조회 UI.

순서: Milestone 1 완료 후 2 시작, 2 완료 후 3과 4 병행 가능.

## 위험 요소 (Risks)

- **트랜잭션 경계 오용**: 적립 예외가 원인 행위를 롤백시키면 REQ-PNT-008 위반. → T5에서 적립 로직 전체를 try-catch로 감싸고, 필요 시 별도 전파-억제 설계(예: 예외 swallow + 로그) 적용. 통합 테스트로 검증(acceptance.md 시나리오).
- **좋아요 동시성**: 동일 사용자가 동시에 like 요청 시 중복 적립 가능성. → `bbs_post_like`의 UNIQUE(user_id, post_id) 제약으로 DB 레벨 차단, insert 충돌 시 적립 스킵.
- **like_count 정합성**: 기존 `bbs_post.like_count`와 `bbs_post_like` row 수 불일치 가능. → like/unlike에서 카운터와 테이블을 같은 트랜잭션으로 갱신.
- **정책 미시드 환경**: 기존 운영 DB에 POINTS:* 키가 없을 때. → REQ-PNT-001 기본값 처리 + V63 seed로 보장.
- **권한 시드 누락**: POINTS:READ/WRITE 권한이 ADMIN 역할에 매핑되지 않으면 관리자 접근 불가. → V63에서 권한 seed 및 역할 매핑 확인.

## 기존 인프라 재사용 정리

- `system_setting` (정책 저장, 신규 테이블 불필요)
- `permissions`/RBAC (POINTS:READ, POINTS:WRITE)
- `@AuditLog` (정책 변경 감사)
- `bbs_post.like_count` (기존 카운터 유지)
- Controller→Service→Mapper + MyBatis XML 패턴
- `EmailTemplateListView.vue` UI 패턴 (필터 카드 + el-table + 모달)
