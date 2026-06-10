---
id: SPEC-CMS-POST-SCHEDULE-001
version: 1.0.0
status: Draft
created: 2026-06-09
updated: 2026-06-09
author: manager-spec
---

# SPEC-CMS-POST-SCHEDULE-001 인수 기준 (acceptance.md)

모든 시나리오는 Given-When-Then 형식. 시각은 서버 타임존(UTC instant) 기준.

---

## AC-PS-001 미래 시각 예약 생성 (REQ-POST-SCHEDULE-001)

- **Given** `DRAFT` 상태의 게시글 `postId=100` 이 존재하고
- **When** 관리자가 `POST /api/v1/board/posts/100/schedule` 에 `scheduledAt = NOW + 1h` 로 요청하면
- **Then** 응답은 `200 OK` 이고, `bbs_post(100).status = 'SCHEDULED'`, `scheduled_at = NOW + 1h` 로 영속화된다
- **And** 응답 `PostDetail.status` 가 `SCHEDULED` 이다

## AC-PS-002 과거 시각 예약 거부 (REQ-POST-SCHEDULE-002-1)

- **Given** 게시글 `postId=100` 이 존재하고
- **When** 관리자가 `scheduledAt = NOW - 1m`(과거)로 schedule 요청하면
- **Then** 응답은 `400 Bad Request` 이고
- **And** `bbs_post(100)` 의 `status` 와 `scheduled_at` 은 변경되지 않는다(부작용 없음)

## AC-PS-003 scheduledAt 누락 거부 (REQ-POST-SCHEDULE-002-2)

- **Given** 게시글 `postId=100` 이 존재하고
- **When** `scheduledAt` 필드 없이 schedule 요청하면
- **Then** `@NotNull` 검증 위반으로 `400 Bad Request` 를 반환한다

## AC-PS-004 배치 만기 자동 발행 (REQ-POST-SCHEDULE-003-1)

- **Given** `postId=101` 이 `status='SCHEDULED'`, `scheduled_at = NOW - 10s`(이미 만기), `deleted_at IS NULL` 이고
- **When** `PostPublishJob` 이 실행되면
- **Then** `bbs_post(101).status = 'PUBLISHED'`, `published_at ≈ NOW`, `scheduled_at IS NULL` 로 전환된다

## AC-PS-005 미만기 게시글 유지 (REQ-POST-SCHEDULE-003-2)

- **Given** `postId=102` 가 `status='SCHEDULED'`, `scheduled_at = NOW + 30m`(미만기)이고
- **When** `PostPublishJob` 이 실행되면
- **Then** `bbs_post(102)` 는 `SCHEDULED` 상태를 유지하며 발행되지 않는다

## AC-PS-006 예약 취소 → DRAFT (REQ-POST-SCHEDULE-004-1)

- **Given** `postId=103` 이 `status='SCHEDULED'`, `scheduled_at` 설정됨이고
- **When** 관리자가 `DELETE /api/v1/board/posts/103/schedule` 요청하면
- **Then** 응답은 `200 OK` 이고 `bbs_post(103).status = 'DRAFT'`, `scheduled_at IS NULL` 이다

## AC-PS-007 비SCHEDULED 취소 거부 (REQ-POST-SCHEDULE-004-2)

- **Given** `postId=104` 가 `status='PUBLISHED'`(예약 아님)이고
- **When** 관리자가 `DELETE /api/v1/board/posts/104/schedule` 요청하면
- **Then** 응답은 `409 Conflict` 이고 상태는 변경되지 않는다

## AC-PS-008 목록 SCHEDULED 배지 (REQ-POST-SCHEDULE-005-1)

- **Given** 관리자 게시글 목록에 `status='SCHEDULED'`, `scheduled_at = 2026-06-10T09:00Z` 인 게시글이 있고
- **When** 관리자가 게시글 목록 화면을 로드하면
- **Then** 해당 행에 `SCHEDULED` 배지와 예약 시각(`2026-06-10 09:00`)이 표시된다

## AC-PS-009 SCHEDULED 공개 미노출 (REQ-POST-SCHEDULE-005-2)

- **Given** `postId=105` 가 `status='SCHEDULED'` 이고
- **When** 시민(공개) 게시글 목록/상세 라우팅이 조회되면
- **Then** `postId=105` 는 결과에 포함되지 않는다(기존 `status='PUBLISHED'` 필터 적용)

## AC-PS-010 존재하지 않는 게시글 예약 (REQ-POST-SCHEDULE-007-1)

- **Given** `postId=999` 게시글이 존재하지 않고
- **When** 관리자가 `POST /api/v1/board/posts/999/schedule` 요청하면
- **Then** 응답은 `404 Not Found`(`PostNotFoundException`)이다

---

## 엣지 케이스

### EDGE-PS-01 DELETED 게시글 예약 거부 (REQ-POST-SCHEDULE-007-2)

- **Given** `postId=106` 이 `status='DELETED'` 이고
- **When** 관리자가 schedule 요청하면
- **Then** `409 Conflict` 를 반환하고 상태를 변경하지 않는다

### EDGE-PS-02 재예약(덮어쓰기) (REQ-POST-SCHEDULE-006-2)

- **Given** `postId=107` 이 이미 `SCHEDULED`, `scheduled_at = NOW + 1h` 이고
- **When** 관리자가 `scheduledAt = NOW + 3h` 로 다시 schedule 요청하면
- **Then** `scheduled_at` 이 `NOW + 3h` 로 덮어써지고 `SCHEDULED` 상태가 유지된다

### EDGE-PS-03 배치 멱등성 (RISK-PS-02)

- **Given** `postId=108` 이 만기(`scheduled_at <= NOW`)이고
- **When** `PostPublishJob` 의 발행 처리가 동일 행에 두 번 호출되면(다중 인스턴스 가정)
- **Then** 첫 호출만 발행을 적용하고, 두 번째 호출은 `WHERE status='SCHEDULED'` 가드로 0행 갱신(부작용 없음)이다

### EDGE-PS-04 서버 다운 중 시각 경과 (RISK-PS-03)

- **Given** `postId=109` 의 `scheduled_at` 이 서버 중단 동안 경과했고
- **When** 서버 재기동 후 첫 `PostPublishJob` tick 이 실행되면
- **Then** `scheduled_at <= NOW()` 조건으로 즉시 발행된다(누락 없음)

### EDGE-PS-05 경계 시각(NOW 동일) 거부 (REQ-POST-SCHEDULE-002-1)

- **Given** 게시글 `postId=110` 이 존재하고
- **When** `scheduledAt` 이 현재 시각과 정확히 동일하면(`isAfter(now)` false)
- **Then** `400 Bad Request` 를 반환한다(미래 시각 아님)

---

## 품질 게이트 기준

- [ ] 모든 AC-PS-001~010 자동화 테스트로 검증(서비스 단위 + 컨트롤러/Mapper IT)
- [ ] 엣지 케이스 EDGE-PS-01~05 검증
- [ ] V43 마이그레이션 CI 적용 성공
- [ ] 백엔드 IT GREEN(과거시각 400 / null 400 / 미존재 404 / 상태가드 409 / 배치 발행)
- [ ] SCHEDULED 공개 미노출 회귀 테스트 통과
- [ ] 프런트엔드 폼 picker + 목록 배지 컴포넌트 테스트 통과
- [ ] LSP 0 에러 / 0 타입에러 / 0 린트에러(run 단계 기준)

## Definition of Done

- spec.md 의 REQ-POST-SCHEDULE-001~007 전부 구현 및 테스트 매핑 완료
- AC-PS-001~010 + EDGE-PS-01~05 전수 GREEN
- `bbs_post.scheduled_at` + `SCHEDULED` CHECK + 부분 인덱스(V43) 적용
- `PostPublishJob` 배치 발행 멱등 동작 확인
- 관리자 폼 예약 발행 + 취소 + 목록 배지 동작
- 시민 공개 라우팅에서 SCHEDULED 비노출 확인
- Page 도메인 원본 로직 무변경(scope discipline)
