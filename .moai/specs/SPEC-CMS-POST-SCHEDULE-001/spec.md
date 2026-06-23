---
id: SPEC-CMS-POST-SCHEDULE-001
version: 1.0.0
status: Implemented
created: 2026-06-09
updated: 2026-06-23
author: manager-spec
priority: P2
related:
  - SPEC-CMS-003 (게시판·공지·Q&A·FAQ 스키마 — bbs_post 원본 테이블/CHECK 제약)
  - SPEC-CMS-005 (콘텐츠 페이지 — Page 예약 발행 패턴 원본, REQ-CONTENT-005-D-4)
issue_number: TBD
---

# SPEC-CMS-POST-SCHEDULE-001 게시글(bbs_post) 예약 발행

## HISTORY

- v1.0.0 / 2026-06-09 / manager-spec / 신규 작성. `Page` 도메인이 이미 보유한 예약 발행 패턴(`status=SCHEDULED` + `scheduled_at` + `schedulePage()` + `scheduledAt > now` 검증)을 `bbs_post` 게시글에 동일하게 적용. 단일 마이그레이션 `V43`(컬럼 1개 추가 + CHECK 제약 DROP/RECREATE), `@Scheduled` 배치 잡 `PostPublishJob`, 백엔드 schedule API 1개, 프런트엔드 일시 선택 UI + SCHEDULED 배지로 구성. 새 도메인·새 테이블 도입 없음.

---

## 1. 개요

### 1.1 목적

관리자가 게시글을 작성한 뒤 **지정한 미래 시각에 자동으로 발행**되도록 예약할 수 있게 한다. 예약된 게시글은 예약 시각이 도래하면 별도 조작 없이 배치 잡에 의해 `PUBLISHED` 상태로 전환된다.

### 1.2 배경

콘텐츠 페이지(`Page`) 도메인에는 예약 발행이 완전히 구현되어 있다:

- `Page.status IN ('DRAFT','SCHEDULED','PUBLISHED','RETRACTED')` + `scheduled_at: Instant`
- `PageService.schedulePage(Long id, PageScheduleRequest req, Long scheduledBy)` — `scheduledAt > now` 검증 후 `status=SCHEDULED` 설정
- `PageScheduleRequest` record(`@NotNull @Future Instant scheduledAt`)
- `PageMapper.schedule(id, scheduledAt)` UPDATE + `PageMapper.findScheduledDue()` 만기 조회
- `POST /api/v1/content/pages/{id}/schedule` 엔드포인트

반면 게시판 게시글(`bbs_post`)에는 예약 발행이 **전혀 없다**:

- `bbs_post.status` CHECK 제약은 `IN ('DRAFT','PUBLISHED','HIDDEN','DELETED')` 로 `SCHEDULED` 미포함
- `scheduled_at` 컬럼 부재
- 게시글 예약 서비스/배치 잡 부재

즉 동일 성격의 발행 도메인에서 **일관성 격차(consistency gap)** 가 존재한다. 본 SPEC 은 검증된 `Page` 패턴을 그대로 차용하여 격차를 메운다(설계 위험 최소).

### 1.3 범위

- `bbs_post` 에 `scheduled_at` 컬럼 추가 및 `SCHEDULED` 상태 허용(단일 마이그레이션 `V43`)
- 예약 생성 API(`scheduledAt > now` 검증) — `Page.schedulePage` 동등
- 예약 취소 API(예약 → DRAFT 복귀)
- `@Scheduled` 배치 잡 `PostPublishJob` — 만기 게시글 자동 발행
- 관리자 게시글 폼의 예약 발행 일시 선택 UI + 목록의 `SCHEDULED` 배지

---

## 2. Page 도메인이 이미 제공하는 것 (재사용 패턴)

| 구성요소 | Page 도메인 (원본) | 본 SPEC 대응 (bbs_post) |
| --- | --- | --- |
| 상태값 | `Page.status` 에 `SCHEDULED` 포함 | `bbs_post.status` 에 `SCHEDULED` 추가 |
| 예약 컬럼 | `page.scheduled_at TIMESTAMPTZ` | `bbs_post.scheduled_at TIMESTAMPTZ` |
| 요청 DTO | `PageScheduleRequest(@NotNull @Future Instant scheduledAt)` | `PostScheduleRequest(@NotNull @Future Instant scheduledAt)` |
| 서비스 메서드 | `PageService.schedulePage(id, req, by)` | `PostService.schedulePost(id, req)` |
| 검증 규칙 | `scheduledAt.isAfter(Instant.now())` | 동일 |
| Mapper | `PageMapper.schedule(id, scheduledAt)` / `findScheduledDue()` | `BbsPostMapper.schedule(...)` / `findScheduledDue()` |
| 엔드포인트 | `POST /api/v1/content/pages/{id}/schedule` | `POST /api/v1/board/posts/{postId}/schedule` |
| 배치 잡 | 만기 발행 배치(`@MX:REASON` 참조: ScheduledPublishJob) | `PostPublishJob` (신규) |

## 3. 본 SPEC 이 신규 도입하는 것 (격차)

- `bbs_post.scheduled_at` 컬럼 + CHECK 제약에 `SCHEDULED` 추가(`V43`)
- `PostScheduleRequest` DTO, `PostService.schedulePost` / `cancelSchedule`
- `BbsPostMapper.schedule` / `clearSchedule` / `publishScheduled` / `findScheduledDue`
- `@Scheduled` 배치 잡 `PostPublishJob`(1분 주기)
- 게시글 폼 예약 일시 picker + 목록 `SCHEDULED` 배지

---

## 4. 데이터 모델

### 4.1 기존 테이블 변경 — `bbs_post`

현재 정의(`V10__board_schema.sql`):

```sql
status       VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED',
published_at TIMESTAMPTZ,
CONSTRAINT chk_bbs_post_status CHECK (status IN ('DRAFT','PUBLISHED','HIDDEN','DELETED')),
```

변경 사항:

- `scheduled_at TIMESTAMPTZ` 컬럼 추가(NULL 허용, 기본 NULL = 예약 없음)
- `chk_bbs_post_status` 제약을 `DROP` 후 `SCHEDULED` 포함하여 `RECREATE`
  (PostgreSQL 은 `ALTER CONSTRAINT` 로 CHECK 식을 수정할 수 없으므로 DROP/ADD 필요)
- 만기 게시글 조회 성능을 위한 부분 인덱스 추가

### 4.2 마이그레이션 (`V43`)

```sql
-- V43__bbs_post_scheduled_publish.sql
-- SPEC-CMS-POST-SCHEDULE-001: 게시글 예약 발행

-- 1) 예약 시각 컬럼
ALTER TABLE bbs_post ADD COLUMN scheduled_at TIMESTAMPTZ;
COMMENT ON COLUMN bbs_post.scheduled_at IS '예약 발행 시각 (NULL=예약 없음, status=SCHEDULED 일 때만 의미)';

-- 2) status CHECK 제약 재정의 (SCHEDULED 추가)
ALTER TABLE bbs_post DROP CONSTRAINT chk_bbs_post_status;
ALTER TABLE bbs_post ADD CONSTRAINT chk_bbs_post_status
  CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','HIDDEN','DELETED'));

-- 3) 만기 예약 게시글 조회용 부분 인덱스
CREATE INDEX idx_bbs_post_scheduled_due ON bbs_post(scheduled_at)
  WHERE status = 'SCHEDULED' AND deleted_at IS NULL;
```

불변식(invariant):

- `status = 'SCHEDULED'` ⇒ `scheduled_at IS NOT NULL` (서비스 계층에서 보장)
- `status = 'PUBLISHED'` 전환 시 `scheduled_at` 은 `NULL` 로 초기화, `published_at = NOW()`

---

## 5. 요구사항 (EARS)

### REQ-POST-SCHEDULE-001 예약 생성 및 영속화

- **REQ-POST-SCHEDULE-001-1 (Event-driven)**
  WHEN 관리자가 게시글 예약 발행을 요청(`POST /api/v1/board/posts/{postId}/schedule`, body `scheduledAt`)하면, THEN 시스템은 해당 게시글의 `status` 를 `SCHEDULED` 로, `scheduled_at` 을 요청 값으로 갱신하고 영속화해야 한다.
- **REQ-POST-SCHEDULE-001-2 (Ubiquitous)**
  시스템은 예약 발행 시각(`scheduled_at`)을 서버 타임존 기준 `TIMESTAMPTZ`(UTC instant)로 저장해야 한다.

### REQ-POST-SCHEDULE-002 예약 시각 검증

- **REQ-POST-SCHEDULE-002-1 (Unwanted behavior)**
  IF 요청된 `scheduledAt` 이 현재 시각(`Instant.now()`) 이후가 아니면(과거 또는 동일), THEN 시스템은 예약을 거부하고 `400 Bad Request` 를 반환해야 하며, `bbs_post` 상태를 변경해서는 안 된다.
- **REQ-POST-SCHEDULE-002-2 (Unwanted behavior)**
  IF `scheduledAt` 이 누락(`null`)되면, THEN 시스템은 `@NotNull` 검증 위반으로 `400 Bad Request` 를 반환해야 한다.

### REQ-POST-SCHEDULE-003 배치 자동 발행

- **REQ-POST-SCHEDULE-003-1 (Event-driven)**
  WHEN 배치 잡 `PostPublishJob` 이 실행되어 `scheduled_at <= NOW() AND status = 'SCHEDULED' AND deleted_at IS NULL` 인 게시글을 발견하면, THEN 시스템은 해당 게시글의 `status` 를 `PUBLISHED`, `published_at` 을 `NOW()` 로 설정하고 `scheduled_at` 을 `NULL` 로 초기화해야 한다.
- **REQ-POST-SCHEDULE-003-2 (State-driven)**
  WHILE 게시글의 `scheduled_at` 이 아직 도래하지 않은 동안(`scheduled_at > NOW()`), 시스템은 해당 게시글을 발행해서는 안 되며 `SCHEDULED` 상태를 유지해야 한다.
- **REQ-POST-SCHEDULE-003-3 (Ubiquitous)**
  시스템은 `PostPublishJob` 을 고정 주기(`@Scheduled`, 1분)로 실행해야 한다.

### REQ-POST-SCHEDULE-004 예약 취소

- **REQ-POST-SCHEDULE-004-1 (Event-driven)**
  WHEN 관리자가 예약을 취소(`DELETE /api/v1/board/posts/{postId}/schedule`)하면, THEN 시스템은 게시글의 `status` 를 `DRAFT` 로 되돌리고 `scheduled_at` 을 `NULL` 로 초기화해야 한다.
- **REQ-POST-SCHEDULE-004-2 (Unwanted behavior)**
  IF 대상 게시글이 `SCHEDULED` 상태가 아니면, THEN 시스템은 취소 요청을 거부하고 `409 Conflict` 를 반환해야 한다.

### REQ-POST-SCHEDULE-005 목록 표시 및 상태 배지

- **REQ-POST-SCHEDULE-005-1 (State-driven)**
  IF 게시글의 `status` 가 `SCHEDULED` 이면, THEN 관리자 게시글 목록은 해당 행에 `SCHEDULED` 배지와 예약 시각(`scheduled_at`)을 표시해야 한다.
- **REQ-POST-SCHEDULE-005-2 (Unwanted behavior)**
  IF 게시글이 `SCHEDULED` 상태이면, THEN 시민(공개) 라우팅/목록은 해당 게시글을 노출해서는 안 된다(발행 전 비공개 유지 — 기존 `status='PUBLISHED'` 필터 재사용).

### REQ-POST-SCHEDULE-006 예약 일시 선택 UI

- **REQ-POST-SCHEDULE-006-1 (Event-driven)**
  WHEN 관리자가 게시글 폼에서 발행 방식으로 "예약 발행" 을 선택하면, THEN 폼은 날짜/시간 picker 를 노출하고, 저장 시 선택된 일시로 schedule API 를 호출해야 한다.
- **REQ-POST-SCHEDULE-006-2 (Optional feature)**
  WHERE 게시글이 이미 `SCHEDULED` 상태로 로드되면, 폼은 기존 예약 시각을 picker 초기값으로 표시하고 재예약(덮어쓰기) 또는 예약 취소를 허용해야 한다.

### REQ-POST-SCHEDULE-007 오류 처리

- **REQ-POST-SCHEDULE-007-1 (Unwanted behavior)**
  IF schedule 대상 게시글 ID 가 존재하지 않으면, THEN 시스템은 `404 Not Found`(`PostNotFoundException`)를 반환해야 한다.
- **REQ-POST-SCHEDULE-007-2 (Unwanted behavior)**
  IF 게시글이 `DELETED` 상태이면, THEN 시스템은 예약을 거부하고 `409 Conflict` 를 반환해야 한다.

---

## 6. API 명세

| 메서드 | 경로 | 설명 | 요청 본문 | 응답 |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/board/posts/{postId}/schedule` | 게시글 예약 발행 | `PostScheduleRequest { scheduledAt: Instant }` | `200 OK` `PostDetail`(status=SCHEDULED) |
| DELETE | `/api/v1/board/posts/{postId}/schedule` | 예약 취소(→DRAFT) | 없음 | `200 OK` `PostDetail`(status=DRAFT) |

응답 코드 요약:

- `200 OK` — 예약/취소 성공
- `400 Bad Request` — `scheduledAt` 과거이거나 누락(REQ-POST-SCHEDULE-002)
- `404 Not Found` — 게시글 없음(REQ-POST-SCHEDULE-007-1)
- `409 Conflict` — 상태 전환 불가(DELETED/비SCHEDULED 취소, REQ-POST-SCHEDULE-004-2 / 007-2)

`PostScheduleRequest`(Page 패턴 동일):

```java
public record PostScheduleRequest(
        @NotNull @Future
        Instant scheduledAt
) {}
```

---

## 7. 기술 접근 (구현 전략)

### 7.1 데이터 (마이그레이션)

- `V43__bbs_post_scheduled_publish.sql` — 4.2 참조. 단일 파일, 컬럼 추가 + CHECK DROP/RECREATE + 부분 인덱스.

### 7.2 백엔드 (additive)

- `kr.co.ircp.cms.domain.board.dto.PostScheduleRequest` 신규(`@NotNull @Future Instant scheduledAt`)
- `kr.co.ircp.cms.domain.board.entity.BbsPostStatus` enum 에 `SCHEDULED` 추가
- `BbsPost` 엔티티에 `private Instant scheduledAt;` 필드 추가(MyBatis resultMap 매핑)
- `BbsPostMapper`:
  - `int schedule(@Param("id") Long id, @Param("scheduledAt") Instant scheduledAt)` — status=SCHEDULED
  - `int clearSchedule(@Param("id") Long id)` — status=DRAFT, scheduled_at=NULL
  - `int publishScheduled(@Param("id") Long id)` — status=PUBLISHED, published_at=NOW(), scheduled_at=NULL
  - `List<BbsPost> findScheduledDue()` — `scheduled_at <= NOW() AND status='SCHEDULED' AND deleted_at IS NULL`
- `PostService.schedulePost(Long id, PostScheduleRequest req)` / `cancelSchedule(Long id)` — `scheduledAt > now` 검증, 상태 전환 가드(`PageServiceImpl.schedulePage` 로직 차용)
- `PostController`(`/api/v1/board/posts`)에 `POST /{postId}/schedule`, `DELETE /{postId}/schedule` 추가
- `kr.co.ircp.cms.domain.board.service.PostPublishJob` — `@Component @Scheduled(cron = "0 * * * * *")` 1분 주기, `findScheduledDue()` 결과를 `publishScheduled()` 로 발행(`PublicationZipExpireJob` 패턴 차용, `@Transactional`)

### 7.3 프런트엔드 (additive)

- 게시글 폼(`PostFormView.vue` 또는 동등 컴포넌트): 발행 방식 라디오(즉시/예약) + ElDatePicker(type=datetime) 추가, 예약 선택 시 schedule API 호출
- 게시글 목록: `status === 'SCHEDULED'` 행에 배지 + 예약 시각 표시(기존 status 배지 매핑 확장)
- API 모듈: `schedulePost(postId, scheduledAt)`, `cancelSchedule(postId)` 추가

### 7.4 영향 범위 (3+ 파일 → 논리 단위 분할)

| 단위 | 파일 | 변경 유형 |
| --- | --- | --- |
| DB | `V43__bbs_post_scheduled_publish.sql` | 신규 |
| 도메인 | `BbsPostStatus.java`, `BbsPost.java` | 수정(값/필드 추가) |
| DTO | `PostScheduleRequest.java` | 신규 |
| 데이터접근 | `BbsPostMapper.java` + XML | 수정(4개 메서드 추가) |
| 서비스 | `PostService.java`, `PostServiceImpl.java` | 수정(2개 메서드 추가) |
| 배치 | `PostPublishJob.java` | 신규 |
| 컨트롤러 | `PostController.java` | 수정(2개 엔드포인트 추가) |
| 프런트엔드 | 게시글 폼/목록/API 모듈 | 수정(picker·배지·API) |

---

## 8. 비기능 요구사항

- **성능**: `findScheduledDue()` 는 부분 인덱스(`idx_bbs_post_scheduled_due`)로 만기 행만 스캔. 배치 1분 주기로 발행 지연 최대 약 1분(허용).
- **동시성**: `PostPublishJob` 은 `@Transactional`. 다중 인스턴스 동시 실행 시 동일 행 중복 발행 방지를 위해 `publishScheduled` 는 `WHERE status='SCHEDULED'` 조건부 UPDATE 로 멱등 보장.
- **일관성**: `SCHEDULED → PUBLISHED` 전환 시 `scheduled_at` 항상 `NULL` 초기화(불변식 4.1).
- **권한**: schedule/cancel 은 관리자 게시글 쓰기 권한과 동일(기존 `PostController` 보안 정책 재사용).

---

## 9. 권한 매트릭스

| 작업 | 관리자(쓰기 권한) | 시민/공개 |
| --- | --- | --- |
| 예약 생성/취소 | 허용 | 거부(401/403) |
| SCHEDULED 게시글 조회 | 허용(관리자 목록) | 거부(노출 안 됨) |
| 자동 발행(배치) | 시스템(주체 없음) | N/A |

---

## 10. 위험 및 대응

| ID | 위험 | 영향 | 대응 |
| --- | --- | --- | --- |
| RISK-PS-01 | CHECK 제약 DROP/RECREATE 중 기존 데이터에 미허용 status 존재 | 마이그레이션 실패 | 기존 값은 모두 허용 집합의 부분집합이므로 안전. 추가만 함(`SCHEDULED`) |
| RISK-PS-02 | 다중 앱 인스턴스 동시 배치 → 중복 발행 | 데이터 일관성 | 조건부 UPDATE(`WHERE status='SCHEDULED'`) 멱등성으로 방어 |
| RISK-PS-03 | 서버 다운 중 예약 시각 경과 | 발행 지연 | 다음 배치 tick 에서 만기 행 일괄 발행(`scheduled_at <= NOW()`) |
| RISK-PS-04 | 클라이언트 로컬타임 ↔ 서버 UTC 불일치 | 의도와 다른 발행 시각 | 프런트엔드가 ISO-8601(오프셋 포함) 전송, 서버는 `Instant` 로 정규화 |

---

## 11. 외부 의존성

없음. 기존 `Page` 예약 발행 패턴, `@Scheduled` 배치 인프라(`PublicationZipExpireJob` 등), `bbs_post` 테이블, `PostController` 보안 정책을 모두 재사용한다. 신규 라이브러리·외부 서비스 도입 없음.

---

## 12. 범위 및 비범위

### 12.1 범위 (포함)

- `bbs_post` 예약 컬럼 + `SCHEDULED` 상태(V43)
- 예약 생성/취소 API + `scheduledAt > now` 검증
- `PostPublishJob` 배치 자동 발행
- 관리자 폼 예약 일시 picker + 목록 SCHEDULED 배지

### 12.2 비범위 (제외)

13장 참조.

---

## 13. Exclusions (What NOT to Build)

- **이메일/푸시 발행 알림**: 예약 게시글이 발행되었을 때 작성자/구독자에게 알림을 보내지 않는다. 알림은 별도 SPEC(`SPEC-CMS-NOTIFICATION-CENTER-001`) 영역.
- **타임존 선택**: 사용자별 타임존 지정을 제공하지 않는다. 모든 예약 시각은 서버 타임존(UTC instant) 기준으로 저장·발행한다.
- **반복 예약(recurring schedules)**: 매주/매일 등 반복 발행 스케줄을 지원하지 않는다. 1회성 단일 예약 시각만 지원.
- **예약 발행 미리보기/대기열 대시보드**: 예약된 게시글만 모아 보는 전용 대기열 화면을 만들지 않는다. 기존 게시글 목록의 status 필터로 대체.
- **콘텐츠 페이지(Page) 도메인 변경**: 원본 `Page` 예약 발행 로직은 수정하지 않는다. 본 SPEC 은 `bbs_post` 만 대상으로 한다.
- **예약 발행 시 자동 SNS 게시/외부 연동**: 외부 채널 발행 트리거는 포함하지 않는다.

---

## 14. 참조 문서

| 문서 | 참조 항목 | 연계 |
| --- | --- | --- |
| `V10__board_schema.sql` | `bbs_post` 정의, `chk_bbs_post_status` | 컬럼/제약 변경 대상 |
| `content/page/service/PageServiceImpl.java` | `schedulePage()` (라인 167~182) | 검증/상태전환 로직 차용 |
| `content/page/dto/PageScheduleRequest.java` | `@NotNull @Future` record | DTO 패턴 차용 |
| `content/page/mapper/PageMapper.java` | `schedule()`, `findScheduledDue()` | Mapper 패턴 차용 |
| `board/service/PublicationZipExpireJob.java` | `@Scheduled @Transactional` | 배치 잡 패턴 차용 |
| `board/controller/PostController.java` | `/api/v1/board/posts`, `{postId}` | 엔드포인트 추가 위치 |

---

## 15. 검증 체크리스트

- [x] `V43` 마이그레이션이 `scheduled_at` 컬럼 + `SCHEDULED` CHECK + 부분 인덱스를 추가한다
- [x] CHECK 제약은 DROP 후 RECREATE 방식(PostgreSQL ALTER CONSTRAINT 미지원 대응)
- [x] `scheduledAt > now` 검증(과거/null 거부) — PostScheduleServiceTest + PostControllerTest
- [x] `PostPublishJob` 이 만기 게시글을 `PUBLISHED` 로 전환하고 `scheduled_at` 을 NULL 로 초기화 — PostIT$ScheduledPublish AC-PS-004
- [x] 예약 취소가 `SCHEDULED → DRAFT` 로 되돌린다 — AC-PS-006
- [x] SCHEDULED 게시글이 공개 라우팅에 노출되지 않는다 — 기존 `status='PUBLISHED'` 필터 재사용(목록/검색 SQL 변경 없음)
- [x] 목록에 SCHEDULED 배지 + 예약 시각 표시 — PostListView.vue
- [x] EARS 5 패턴 사용(Ubiquitous/Event-driven/State-driven/Unwanted/Optional)
- [x] Exclusions 섹션 존재(이메일 알림/타임존/반복 예약 명시 제외)

---

## 16. Acceptance Criteria 요약 (상세 시나리오는 acceptance.md)

| AC | 요약 | 매핑 REQ |
| --- | --- | --- |
| AC-PS-001 | 미래 시각 예약 → status=SCHEDULED + scheduled_at 저장 | REQ-POST-SCHEDULE-001 |
| AC-PS-002 | 과거 시각 예약 → 400, 상태 미변경 | REQ-POST-SCHEDULE-002-1 |
| AC-PS-003 | scheduledAt 누락 → 400 | REQ-POST-SCHEDULE-002-2 |
| AC-PS-004 | 배치 만기 발행 → PUBLISHED + published_at + scheduled_at NULL | REQ-POST-SCHEDULE-003-1 |
| AC-PS-005 | 미만기 게시글은 SCHEDULED 유지 | REQ-POST-SCHEDULE-003-2 |
| AC-PS-006 | 예약 취소 → DRAFT 복귀 | REQ-POST-SCHEDULE-004-1 |
| AC-PS-007 | 비SCHEDULED 취소 → 409 | REQ-POST-SCHEDULE-004-2 |
| AC-PS-008 | 목록 SCHEDULED 배지 + 예약 시각 표시 | REQ-POST-SCHEDULE-005-1 |
| AC-PS-009 | SCHEDULED 게시글 공개 미노출 | REQ-POST-SCHEDULE-005-2 |
| AC-PS-010 | 존재하지 않는 게시글 예약 → 404 | REQ-POST-SCHEDULE-007-1 |
