---
id: SPEC-CMS-POINTS-001
version: 0.1.0
status: draft
created: 2026-06-17
updated: 2026-06-17
author: ircp
priority: medium
issue_number: 0
---

# SPEC-CMS-POINTS-001 게시판/댓글 참여 포인트 지급 시스템

## HISTORY

- v0.1.0 (2026-06-17): 최초 작성. 게시글/댓글/좋아요 활동 기반 참여 포인트 지급, 관리자 정책 설정, 사용자 내역 조회를 정의.

## 개요 (Overview)

사용자가 게시글 작성, 댓글 작성, 추천(좋아요) 활동을 수행하면 정의된 정책에 따라 포인트를 부여하는 참여 포인트 시스템이다. 커뮤니티 참여를 장려하기 위한 게이미피케이션 기능으로, 다음 세 가지 핵심 가치를 제공한다.

- 사용자 참여 활동에 대한 자동 포인트 적립 (게시글/댓글/좋아요)
- 관리자가 이벤트별 포인트 값과 시스템 전체 활성화 여부를 설정
- 사용자가 자신의 포인트 누적 총액과 적립 내역을 조회, 관리자가 전체 사용자 내역을 조회

포인트 정책은 기존 `system_setting` 키-값 저장소를 재사용하여 별도 정책 테이블 없이 운영한다. 포인트 적립 트랜잭션은 append-only 원장(`user_point_ledger`)에 기록하고, 성능을 위해 사용자별 누적 총액을 비정규화 테이블(`user_point_summary`)에 별도 보관한다.

## 범위 (Scope)

본 SPEC은 다음을 포함한다.

- 포인트 정책 조회 및 관리 (system_setting 기반): `POINTS:ENABLED`, `POINTS:POST_CREATED`, `POINTS:COMMENT_CREATED`, `POINTS:LIKE_GIVEN`
- 게시글 작성 시 포인트 적립 (`PostServiceImpl.createPost()` 트랜잭션 연동)
- 댓글 작성 시 포인트 적립 (`CommentServiceImpl.createComment()` 트랜잭션 연동)
- 게시글 좋아요 최초 1회 적립 및 1인 1게시글 중복 방지 (신규 `bbs_post_like` 테이블)
- 포인트 적립 원장(`user_point_ledger`) 및 누적 요약(`user_point_summary`) 관리
- 관리자 포인트 정책 화면 (이벤트별 값 설정, 시스템 ON/OFF 토글)
- 관리자 포인트 내역 화면 (사용자/이벤트/기간 검색)
- 사용자 본인 포인트 총액 및 내역 조회
- 신규 도메인 패키지 `kr.co.ircp.cms.domain.point`
- DB 마이그레이션 `V59__points_system.sql`

## 비범위 (Out of Scope)

- **포인트 사용/차감/소멸 (Redemption)**: 적립된 포인트를 상품 교환, 등급 산정, 쿠폰 전환 등으로 소비하거나 차감, 만료시키는 기능은 본 SPEC에 포함하지 않는다. 본 SPEC은 적립(earn-only)에만 집중한다.
- **좋아요 취소 시 포인트 회수**: 사용자가 좋아요를 취소하더라도 이미 적립된 `POINTS:LIKE_GIVEN` 포인트는 차감하지 않는다 (REQ-PNT-004). 좋아요 토글을 통한 포인트 어뷰징 방지가 목적이며, 부정 적립 정밀 차단은 별도 SPEC에서 다룬다.
- **포인트 어뷰징 탐지/제재**: 단시간 대량 게시/삭제 후 재게시 등 어뷰징 패턴의 탐지, 차단, 회수 정책은 본 SPEC 범위가 아니다.
- **등급/레벨 시스템**: 포인트 누적에 따른 사용자 등급, 뱃지, 레벨업 기능은 포함하지 않는다.
- **포인트 알림**: 포인트 적립 시 사용자에게 인앱/이메일 알림을 보내는 기능은 포함하지 않는다 (기존 notification 도메인 연계는 후속 SPEC).

## EARS 요구사항 (Requirements)

### REQ-PNT-001: 포인트 정책 조회

WHEN 시스템이 포인트 정책 설정을 로드할 때, the 시스템 SHALL `system_setting` 테이블에서 `POINTS:ENABLED`, `POINTS:POST_CREATED`, `POINTS:COMMENT_CREATED`, `POINTS:LIKE_GIVEN` 키의 값을 읽어와 정책으로 사용한다.

IF 해당 정책 키가 존재하지 않으면, then the 시스템 SHALL 안전한 기본값(`POINTS:ENABLED=false`, 각 이벤트 포인트 `0`)을 적용하여 포인트를 적립하지 않는다.

### REQ-PNT-002: 게시글 작성 포인트

WHEN 사용자가 게시글을 정상적으로 작성(생성)할 때, the 시스템 SHALL `POINTS:POST_CREATED` 정책에 정의된 포인트를 해당 사용자(`authorId`)에게 적립하고 `user_point_ledger`에 적립 내역을 기록한다.

### REQ-PNT-003: 댓글 작성 포인트

WHEN 사용자가 댓글을 정상적으로 작성(생성)할 때, the 시스템 SHALL `POINTS:COMMENT_CREATED` 정책에 정의된 포인트를 해당 사용자(`authorId`)에게 적립하고 `user_point_ledger`에 적립 내역을 기록한다.

### REQ-PNT-004: 좋아요 포인트

WHEN 사용자가 특정 게시글에 처음으로 좋아요를 누를 때, the 시스템 SHALL `bbs_post_like`에 (userId, postId) 레코드를 생성하고 `POINTS:LIKE_GIVEN` 정책에 정의된 포인트를 해당 사용자에게 적립한다.

WHILE 동일 사용자가 동일 게시글에 이미 좋아요 레코드를 보유한 상태일 때, the 시스템 SHALL 좋아요 재요청에 대해 포인트를 추가 적립하지 않는다.

IF 사용자가 좋아요를 취소(unlike)하면, then the 시스템 SHALL 이미 적립된 포인트를 차감하지 않는다.

### REQ-PNT-005: 포인트 정책 관리

WHEN 관리자가 포인트 정책 관리 화면에서 이벤트별 포인트 값 또는 활성화 여부를 변경할 때, the 시스템 SHALL 해당 변경을 `system_setting`에 저장하고 변경 행위를 감사 로그(`@AuditLog`)로 기록한다.

The 시스템 SHALL 포인트 정책 변경 API를 `POINTS:WRITE` 권한 보유자(관리자)에게만 허용한다.

### REQ-PNT-006: 사용자 포인트 내역 조회

WHEN 관리자가 포인트 내역 화면에서 사용자/이벤트/기간 조건으로 조회를 요청할 때, the 시스템 SHALL `user_point_ledger`를 조건에 맞게 필터링하여 페이지 단위로 반환한다.

WHEN 인증된 사용자가 자신의 포인트 내역을 요청할 때, the 시스템 SHALL 해당 사용자 본인의 `user_point_ledger` 내역과 `user_point_summary` 누적 총액만을 반환한다.

The 시스템 SHALL 사용자 본인이 타인의 포인트 내역을 조회하지 못하도록 한다.

### REQ-PNT-007: 포인트 시스템 활성화 토글

WHILE `POINTS:ENABLED` 설정이 `false`일 때, the 시스템 SHALL 게시글/댓글/좋아요 활동이 발생하더라도 어떠한 포인트도 적립하지 않는다.

WHEN `POINTS:ENABLED`가 `true`로 설정될 때, the 시스템 SHALL 이후 발생하는 활동부터 정책에 따라 포인트 적립을 재개한다.

### REQ-PNT-008: 포인트 적립 원자성 (Best-Effort)

IF 포인트 적립 처리가 실패하더라도, then the 시스템 SHALL 게시글/댓글 작성 및 좋아요 등록 등 원인 행위는 정상적으로 완료시킨다 (포인트 적립은 best-effort 보조 기능이며 핵심 행위를 차단해서는 안 된다).

The 시스템 SHALL 포인트 적립 실패를 오류로 전파하지 않고 로그로 기록한다.

> 참고: REQ-PNT-002/003/004의 적립은 원인 행위와 동일한 `@Transactional` 경계 내에서 수행하되, 적립 로직 자체의 예외는 catch하여 원인 행위 트랜잭션을 롤백시키지 않도록 설계한다 (plan.md T5 참조).

## 기술 접근법 (Technical Approach)

세 개의 영속 구조와 한 개의 서비스로 구성한다.

- **정책 (Policy)**: 별도 테이블 없이 기존 `system_setting`(key-value, STRING/INT/BOOL 타입) 재사용. `PointPolicyService`가 키를 읽어 `PointPolicy` 값 객체로 캐싱 없이 매 적립 시 조회(정책 변경 즉시 반영, REQ-PNT-007).
- **원장 (Ledger)**: `user_point_ledger`는 append-only 거래 로그. (userId, eventType, points, refType, refId, createdAt) 형태로 모든 적립 이벤트를 1행씩 기록. 감사/조회의 단일 진실 원천.
- **요약 (Summary)**: `user_point_summary`는 사용자별 누적 총액(totalPoints, updatedAt)을 비정규화하여 보관. 적립 시 ledger insert와 summary upsert를 함께 수행하여 총액 조회 성능 확보.
- **좋아요 추적 (Like Tracking)**: `bbs_post_like`(userId, postId, createdAt, UNIQUE(userId, postId)) 신규 테이블로 1인 1게시글 좋아요를 추적. 기존 `bbs_post.like_count` 카운터는 유지하되 per-user 적립 판단은 이 테이블의 UNIQUE 제약으로 수행.
- **적립 진입점 (Award Entry)**: `UserPointService.awardPoints(userId, eventType, refType, refId)`를 `PostServiceImpl.createPost()`, `CommentServiceImpl.createComment()`, `BbsPostLikeService.like()` 내부에서 호출. 적립 로직은 내부 try-catch로 감싸 best-effort 보장(REQ-PNT-008).

기존 패턴 준수: Controller → Service(`@Service`, `@RequiredArgsConstructor`, `@Transactional`) → Mapper(MyBatis). 관리자 화면은 `EmailTemplateListView.vue`의 필터 카드 + `el-table` + 모달 패턴을 따른다.

## 영향받는 파일 목록 (Affected Files)

### 신규 (Backend)

- `[DELTA] backend/src/main/resources/db/migration/V59__points_system.sql` — user_point_ledger, user_point_summary, bbs_post_like 테이블 + POINTS:* system_setting seed + POINTS:READ/POINTS:WRITE 권한 seed
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/entity/UserPointLedger.java`
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/entity/UserPointSummary.java`
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/board/entity/BbsPostLike.java`
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/mapper/UserPointLedgerMapper.java` (+ XML)
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/mapper/UserPointSummaryMapper.java` (+ XML)
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/board/mapper/BbsPostLikeMapper.java` (+ XML)
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/service/PointPolicyService.java`
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/service/UserPointService.java` (+ Impl)
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/board/service/BbsPostLikeService.java` (+ Impl)
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/controller/PointPolicyController.java`
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/controller/PointLedgerController.java`
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/point/dto/` (PointPolicyDto, PointLedgerSearchDto, PointSummaryDto 등)

### 수정 (Backend)

- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/board/service/PostServiceImpl.java` — createPost() 내 awardPoints 호출 추가
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/board/service/CommentServiceImpl.java` — createComment() 내 awardPoints 호출 추가
- `[DELTA] backend/src/main/java/kr/co/ircp/cms/domain/board/controller/` — 게시글 좋아요(like/unlike) 엔드포인트 추가 또는 연동

### 신규 (Frontend / Admin SPA)

- `[DELTA] frontend/src/views/.../PointPolicyAdminView.vue` — 이벤트별 포인트 값 + 시스템 ON/OFF
- `[DELTA] frontend/src/views/.../PointLedgerAdminView.vue` — 사용자/이벤트/기간 검색 내역
- `[DELTA] frontend/src/views/.../UserPointHistoryView.vue` (또는 프로필 영역 컴포넌트) — 본인 총액 + 내역
- `[DELTA] frontend/src/api/point.ts` — 포인트 API 클라이언트
- `[DELTA] frontend/src/router/` — 신규 라우트 + meta.permissions 등록
