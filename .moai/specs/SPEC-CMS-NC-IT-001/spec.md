---
id: SPEC-CMS-NC-IT-001
title: "AdminNotificationController 통합 테스트"
status: Implemented
version: 1.0.0
created_at: 2026-06-11
updated_at: 2026-06-12
---

## HISTORY

| 버전 | 날짜 | 내용 |
|------|------|------|
| v1.0.1 | 2026-06-12 | 구현 완료 (Implemented). 커밋 d8433e1 — `AdminNotificationControllerIT.java` 신규 (384 lines), 14개 테스트 케이스 (AC-NC-IT-001~008) 전체 PASSED. CHANGELOG.md 업데이트. |
| v1.0.0 | 2026-06-11 | 최초 작성 |

# SPEC-CMS-NC-IT-001: AdminNotificationController 통합 테스트

## 1. 개요

`AdminNotificationController` (`/api/v1/admin/notifications`)의 전체 엔드포인트를 검증하는
통합 테스트(IT) 를 추가한다.

기존 SPEC-CMS-NOTIFICATION-CENTER-001 에서 컨트롤러·서비스·매퍼·마이그레이션은 모두 구현됐으나
IT 테스트가 누락되어 있다. 이 SPEC 은 해당 공백을 채운다.

## 2. 배경

| 항목 | 상태 |
|------|------|
| 백엔드 구현 | 완료 (SPEC-CMS-NOTIFICATION-CENTER-001) |
| 프론트엔드 구현 | 완료 (NotificationCenterView.vue) |
| 단위 테스트 | 완료 (AdminNotificationServiceTest) |
| **IT 테스트** | **미존재 ← 이 SPEC의 목표** |

## 3. 인수 조건 (EARS 형식)

### AC-NC-IT-001: 목록 조회

- WHEN 인증된 ADMIN 이 `GET /api/v1/admin/notifications` 를 호출하면
  THEN 200 OK 와 `PageResponse<AdminNotificationDto>` 구조를 반환한다

### AC-NC-IT-002: 상태 필터

- WHEN `?status=UNREAD` 파라미터를 전달하면
  THEN 해당 adminUserId 의 UNREAD 알림만 반환한다
- WHEN `?status=ARCHIVED` 파라미터를 전달하면
  THEN ARCHIVED 알림만 반환한다

### AC-NC-IT-003: 개별 읽음 처리

- WHEN `PATCH /api/v1/admin/notifications/{id}/read` 를 호출하면
  THEN 204 No Content 를 반환하고 DB status=READ, read_at IS NOT NULL

### AC-NC-IT-004: 일괄 읽음 처리

- WHEN `PATCH /api/v1/admin/notifications/read-all` 을 호출하면
  THEN 200 OK 와 `{"updatedCount": N}` 반환, UNREAD 알림 전체가 READ 로 전환

### AC-NC-IT-005: 보관 처리

- WHEN `PATCH /api/v1/admin/notifications/{id}/archive` 를 호출하면
  THEN 204 No Content, DB status=ARCHIVED, archived_at IS NOT NULL

### AC-NC-IT-006: 미읽음 수

- WHEN `GET /api/v1/admin/notifications/unread-count` 를 호출하면
  THEN 200 OK 와 `{"unreadCount": N}` 을 반환한다

### AC-NC-IT-007: 권한 가드

- WHEN 비인증 요청 → 401
- WHEN USER 권한 → 403
- WHEN CONTENT_ADMIN 권한 → 200 (허용)

### AC-NC-IT-008: 사용자 격리

- WHEN adminUser-A 의 알림을 adminUser-B 가 읽음 처리 시도하면
  THEN 204 이지만 DB 상태 변경 없음 (0 rows updated → 서비스 조용히 처리)

## 4. 구현 범위

- 신규 파일:
  `backend/src/test/java/kr/co/ircp/cms/domain/notification/AdminNotificationControllerIT.java`
- 수정 파일: 없음

## 5. 비기능 요건

- IT 테스트는 `AbstractIntegrationTest` 를 상속한다
- `@Tag("integration")` 은 슈퍼클래스에서 상속
- `insertAdminNotification()` 헬퍼로 직접 DB INSERT
- MockitoBean: JwtTokenProvider, TokenBlacklistMapper
- AuthorizationCoverageArchTest baseline(126) 에 영향 없음

## 6. 관련 파일

- `AdminNotificationController.java`
- `AdminNotificationService.java`
- `AdminNotificationMapper.java`
- `AdminNotificationMapper.xml`
- `V40__admin_notification.sql`
