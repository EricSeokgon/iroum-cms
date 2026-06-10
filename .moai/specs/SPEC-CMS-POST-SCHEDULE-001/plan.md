---
id: SPEC-CMS-POST-SCHEDULE-001
version: 1.0.0
status: Draft
created: 2026-06-09
updated: 2026-06-09
author: manager-spec
---

# SPEC-CMS-POST-SCHEDULE-001 구현 계획 (plan.md)

## 1. 구현 전략 개요

`Page` 도메인의 검증된 예약 발행 패턴을 `bbs_post` 게시글에 동등 이식한다. 신규 설계가 아니라 **패턴 차용 + 격차 보완**이므로 위험이 낮다. 변경은 데이터 → 백엔드 → 프런트엔드 순으로 의존성 방향에 따라 진행한다.

핵심 원칙:

- 단일 마이그레이션 `V43` (additive, CHECK DROP/RECREATE)
- 백엔드 메서드 시그니처/검증 로직은 `PageServiceImpl.schedulePage` 와 대칭
- 배치 잡은 `PublicationZipExpireJob` 의 `@Component @Scheduled @Transactional` 형태 차용
- 멱등 발행(조건부 UPDATE)으로 다중 인스턴스 안전

## 2. 마일스톤 (우선순위 기반, 시간 추정 없음)

### M1 — 데이터 계층 (Priority High)

- `V43__bbs_post_scheduled_publish.sql` 작성
  - `ALTER TABLE bbs_post ADD COLUMN scheduled_at TIMESTAMPTZ`
  - `chk_bbs_post_status` DROP 후 `SCHEDULED` 포함 RECREATE
  - `idx_bbs_post_scheduled_due` 부분 인덱스
- 검증: 로컬/CI 마이그레이션 적용, 기존 데이터 제약 위반 없음 확인
- 선행: 없음 / 후행: M2

### M2 — 백엔드 도메인·데이터 접근 (Priority High)

- `BbsPostStatus` enum 에 `SCHEDULED` 추가
- `BbsPost` 엔티티 `scheduledAt` 필드 + resultMap 매핑
- `BbsPostMapper` (+ XML): `schedule`, `clearSchedule`, `publishScheduled`, `findScheduledDue`
- 검증: Mapper IT (실제 DB) — schedule/clear/publish/due 조회 동작
- 선행: M1 / 후행: M3

### M3 — 백엔드 서비스·API (Priority High)

- `PostScheduleRequest` DTO 신규
- `PostService.schedulePost(id, req)` / `cancelSchedule(id)` — `scheduledAt > now` 검증, 상태 가드(DELETED/비SCHEDULED)
- `PostController`: `POST /{postId}/schedule`, `DELETE /{postId}/schedule`
- 검증: 서비스 단위 테스트(과거시각 400, null 400, 미존재 404, 상태 가드 409) + 컨트롤러 IT
- 선행: M2 / 후행: M4, M5

### M4 — 배치 자동 발행 (Priority High)

- `PostPublishJob` (`@Scheduled(cron = "0 * * * * *")`, `@Transactional`)
- `findScheduledDue()` → `publishScheduled()` 루프(멱등 조건부 UPDATE)
- 검증: 만기/미만기/멱등(중복 호출) IT
- 선행: M2 / 후행: 없음

### M5 — 프런트엔드 UI (Priority Medium)

- 게시글 폼: 발행 방식 라디오(즉시/예약) + ElDatePicker(datetime), 저장 시 schedule API 호출
- 게시글 목록: `SCHEDULED` 배지 + 예약 시각 표시
- API 모듈: `schedulePost`, `cancelSchedule`
- 검증: 폼 컴포넌트 단위 테스트 + 목록 배지 렌더 테스트
- 선행: M3 / 후행: 없음

### M6 — 통합 검증 (Priority Medium)

- acceptance.md 의 AC-PS-001~010 시나리오 전수 검증
- 공개 라우팅 SCHEDULED 비노출 회귀 확인
- 선행: M3, M4, M5

## 3. 기술 접근 상세

### 3.1 마이그레이션 (M1)

PostgreSQL 은 CHECK 식을 `ALTER CONSTRAINT` 로 변경할 수 없으므로 반드시 DROP 후 ADD. 기존 status 값 집합(`DRAFT/PUBLISHED/HIDDEN/DELETED`)은 새 허용 집합의 부분집합이므로 데이터 위반 없음. 컬럼은 NULL 허용으로 추가하여 기존 행에 영향 없음.

### 3.2 서비스 검증 로직 (M3)

`PageServiceImpl.schedulePage` 와 동일하게:

- `findById().orElseThrow(PostNotFoundException)`
- `if (!req.scheduledAt().isAfter(Instant.now())) throw 400`
- `DELETED` 상태면 409
- `mapper.schedule(id, scheduledAt)` 후 메모리 객체 갱신 반환

`cancelSchedule`:

- 대상이 `SCHEDULED` 아니면 409
- `mapper.clearSchedule(id)` → status=DRAFT, scheduled_at=NULL

### 3.3 배치 멱등성 (M4)

`publishScheduled` 는 `UPDATE bbs_post SET status='PUBLISHED', published_at=NOW(), scheduled_at=NULL WHERE id=? AND status='SCHEDULED'` 형태. 다중 인스턴스가 동일 행을 처리해도 `WHERE status='SCHEDULED'` 가드로 한 번만 적용된다.

### 3.4 프런트엔드 시각 처리 (M5)

picker 값은 ISO-8601(타임존 오프셋 포함)로 직렬화하여 전송, 서버가 `Instant` 로 정규화. 클라이언트 로컬타임 ↔ 서버 UTC 불일치(RISK-PS-04) 방지.

## 4. 위험 및 대응 (요약)

| 위험 | 대응 |
| --- | --- |
| CHECK DROP/RECREATE 데이터 위반 | 기존 값은 부분집합 — 안전, 추가만 함 |
| 다중 인스턴스 중복 발행 | 조건부 UPDATE 멱등성 |
| 서버 다운 중 시각 경과 | 다음 tick 에서 `scheduled_at <= NOW()` 일괄 발행 |
| 타임존 불일치 | ISO-8601 오프셋 전송 + 서버 Instant 정규화 |

(상세: spec.md 10장)

## 5. 의존성

없음. 기존 `Page` 패턴, `@Scheduled` 인프라, `bbs_post`, `PostController` 보안 정책 재사용. 신규 외부 라이브러리/서비스 없음.

## 6. 완료 정의 (Definition of Done) — 요약

- V43 마이그레이션 적용 성공(CI 포함)
- 백엔드 schedule/cancel API + 배치 발행 동작 + IT GREEN
- 프런트엔드 예약 picker + SCHEDULED 배지 렌더
- acceptance.md AC-PS-001~010 전수 통과
- SCHEDULED 공개 미노출 회귀 통과

(상세 DoD 는 acceptance.md 참조)
