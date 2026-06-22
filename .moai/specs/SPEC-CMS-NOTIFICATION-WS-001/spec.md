---
id: SPEC-CMS-NOTIFICATION-WS-001
title: 관리자 알림 WebSocket 실시간 푸시
status: draft
version: 0.1.0
created_at: 2026-06-23
updated_at: 2026-06-23
author: manager-spec (MoAI)
priority: P2
depends_on:
  - SPEC-CMS-NOTIFICATION-CENTER-001
---

# SPEC-CMS-NOTIFICATION-WS-001 — 관리자 알림 WebSocket 실시간 푸시

## HISTORY

- 2026-06-23 (v0.1.0): 최초 작성 (Draft). SPEC-CMS-NOTIFICATION-CENTER-001 의 30초 폴링을 Spring WebSocket + STOMP 기반 실시간 푸시로 교체. 헤더 배지·알림 센터 화면 구독 연동. SockJS 폴백, Vue composable `useNotificationWs`, 재연결 시 폴링 폴백 포함. 스키마 변경 없음. manager-spec (MoAI).

---

## 1. 개요

### 1.1 목적

SPEC-CMS-NOTIFICATION-CENTER-001 이 구현한 관리자 알림 센터는 미읽음 수를 **30초 간격 HTTP 폴링**으로 갱신한다. 이로 인해 신규 알림 인식 지연이 최대 30초 발생하고, 동시 접속 관리자 수에 비례하여 불필요한 서버 요청이 누적된다.

본 SPEC 은 폴링을 **WebSocket + STOMP 실시간 푸시**로 교체하여 알림 지연을 0~1초 이내로 단축하고 서버 부하를 낮춘다. 기존 REST API(`GET /unread-count` 등)와 `admin_notification` 테이블은 **변경하지 않는다**.

### 1.2 범위

**포함:**
- Spring WebSocket + STOMP 엔드포인트 (`/ws/notifications`)
- 서버: `admin_notification` INSERT 발생 시 해당 관리자에게 즉시 STOMP 메시지 푸시
- 프론트: Vue 3 composable `useNotificationWs` — 헤더 배지 및 알림 센터 화면에서 구독
- SockJS 폴백 (WebSocket 미지원 환경)
- 연결 끊김 시 30초 폴링 폴백 → 재연결 성공 시 폴링 중단
- Spring Security WebSocket 인증 (기존 JWT/세션 재사용)

**제외:**
- `admin_notification` 스키마 변경 (추가 컬럼·인덱스 없음)
- 기존 REST API 엔드포인트 변경·삭제
- 시민 사용자용 알림(`user_notification_inbox`) WebSocket 연동 — 별도 SPEC
- 브라우저 Notification API (OS 레벨 푸시)
- 알림 내용 본문 스트리밍 (배지 카운트 + 요약 메타만 푸시)

---

## 2. 기존 구현 재사용 (변경 금지)

| 자산 | 위치 | 본 SPEC 의 사용 방식 |
|---|---|---|
| `admin_notification` 테이블 | SPEC-CMS-NOTIFICATION-CENTER-001 §5.1 (V40) | WebSocket 메시지 페이로드 소스 (스키마 변경 없음) |
| `AdminNotificationController` | `kr.co.ircp.cms.notification.controller` | REST API 유지 — 초기 목록 로드·읽음 처리 등에 계속 사용 |
| `GET /api/v1/admin/notifications/unread-count` | 기존 REST API | WebSocket 연결 실패·재연결 중 폴백 폴링용으로 유지 |
| `NotificationCenterView.vue` | `/admin/notifications` | WebSocket 구독 추가 (폴링 코드 제거) |
| AppHeader 의 미읽음 배지 | `AppHeader.vue` (setInterval 30s) | `setInterval` 제거 → `useNotificationWs` 구독으로 교체 |
| RBAC — SUPER_ADMIN / CONTENT_ADMIN | SPEC-CMS-RBAC-001 | WebSocket 핸드셰이크 인증에 동일 권한 적용 |
| JWT 인증 필터 | Spring Security | WebSocket 핸드셰이크 단계에서 동일 토큰 검증 |

→ 위 자산은 **수정하거나 대체하지 않는다.** WebSocket 레이어는 기존 REST API 위에 **추가(additive)** 된다.

---

## 3. 신규 도입 (격차 분석)

| 격차 | 현재 상태 | 본 SPEC 해결 방식 |
|---|---|---|
| 알림 실시간 수신 | 최대 30초 지연 (HTTP 폴링) | STOMP 푸시로 0~1초 이내 수신 |
| 서버 요청 부하 | 관리자 1인당 분당 2회 HTTP 요청 | WebSocket 지속 연결로 연결 유지 비용만 존재 |
| 연결 끊김 처리 | 없음 (폴링이므로 자동 재시도) | SockJS 재연결 + 폴링 폴백 로직 |
| WebSocket 인증 | 미구현 | 핸드셰이크 단계 JWT 검증 + Spring Security WS |
| 개인화 푸시 토픽 | 미구현 | `/user/{userId}/queue/notifications` 개인 큐 |
| Vue WS 공통 처리 | 미구현 | `useNotificationWs` composable (연결·재연결·폴백 캡슐화) |

---

## 4. 데이터 모델

### 4.1 스키마 변경

**없음.** `admin_notification` 테이블(V40)을 그대로 사용한다. 추가 마이그레이션 파일 없음.

### 4.2 WebSocket 메시지 형식

WebSocket 으로 전송되는 메시지는 JSON 형식이며, 두 가지 페이로드 타입을 사용한다.

#### 4.2.1 알림 푸시 메시지 (`NotificationPushPayload`)

서버가 신규 `admin_notification` INSERT 직후 해당 관리자에게 발행한다.

```json
{
  "type": "NOTIFICATION",
  "id": 123,
  "notificationType": "POST_APPROVAL_REQUEST",
  "severity": "INFO",
  "title": "게시글 승인 요청이 도착했습니다",
  "refType": "POST",
  "refId": 456,
  "createdAt": "2026-06-23T10:00:00Z",
  "unreadCount": 5
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `type` | `"NOTIFICATION"` | 메시지 타입 식별자 |
| `id` | Long | `admin_notification.id` |
| `notificationType` | String | `admin_notification.type` |
| `severity` | `INFO` \| `WARN` \| `ERROR` | 알림 심각도 |
| `title` | String | 알림 제목 (최대 200자) |
| `refType` | String? | 딥링크 리소스 타입 |
| `refId` | Long? | 딥링크 리소스 ID |
| `createdAt` | ISO-8601 UTC | 생성 시각 |
| `unreadCount` | Int | 현재 미읽음 총 수 (배지용) |

#### 4.2.2 연결 확인 메시지 (`ConnectionAckPayload`)

핸드셰이크 완료 직후 서버가 구독자에게 1회 발행한다.

```json
{
  "type": "CONNECTED",
  "unreadCount": 3
}
```

---

## 5. API 설계

### 5.1 WebSocket 엔드포인트

| 항목 | 값 |
|---|---|
| WebSocket 엔드포인트 | `ws(s)://{host}/ws/notifications` |
| SockJS 폴백 URL | `https://{host}/ws/notifications` (SockJS HTTP 폴링) |
| STOMP 브로커 중계 prefix | `/topic` (브로드캐스트, 미사용), `/user` (개인 큐) |
| 앱 목적지 prefix | `/app` |
| 인증 방식 | HTTP 핸드셰이크 헤더 `Authorization: Bearer {JWT}` |

### 5.2 STOMP 목적지

| 방향 | 목적지 | 설명 |
|---|---|---|
| 서버 → 클라이언트 | `/user/queue/notifications` | 개인 알림 푸시 (신규 알림 발생 시) |
| 서버 → 클라이언트 | `/user/queue/notifications/ack` | 연결 확인 (CONNECTED 메시지) |
| 클라이언트 → 서버 | `/app/notifications/ping` | 클라이언트 생존 확인 (선택, heartbeat 대체 가능) |

> `/user/queue/notifications` 는 Spring의 `SimpMessagingTemplate.convertAndSendToUser()` 를 사용하며, 실제 구독 경로는 `/user/{userId}/queue/notifications` 로 자동 변환된다.

### 5.3 기존 REST API (변경 없음)

WebSocket 폴백 및 초기 데이터 로드에 계속 사용한다.

| 메서드 | 경로 | 용도 |
|---|---|---|
| `GET` | `/api/v1/admin/notifications` | 알림 목록 조회 (초기 로드) |
| `GET` | `/api/v1/admin/notifications/unread-count` | 폴백 폴링용 미읽음 수 조회 |
| `PATCH` | `/api/v1/admin/notifications/{id}/read` | 개별 읽음 처리 |
| `PATCH` | `/api/v1/admin/notifications/read-all` | 일괄 읽음 처리 |

---

## 6. 요구사항 (EARS 형식)

### REQ-NWS-001 — WebSocket 엔드포인트 구성

**WHEN** 관리자(SUPER_ADMIN 또는 CONTENT_ADMIN)가 `/ws/notifications` 에 WebSocket 연결을 요청하는 경우, **THE SYSTEM SHALL** JWT 토큰을 핸드셰이크 단계에서 검증하고, 유효한 경우 STOMP 세션을 수립한다.

- 유효하지 않은 토큰(만료·서명 오류·권한 부족)은 HTTP 401 로 핸드셰이크를 거부한다.
- SockJS 폴백 경로를 동일 엔드포인트에서 지원한다.
- 연결 성공 직후 서버는 `/user/queue/notifications/ack` 로 `CONNECTED` 메시지(현재 unreadCount 포함)를 1회 발행한다.

### REQ-NWS-002 — 신규 알림 실시간 푸시

**WHEN** 어느 서비스가 `admin_notification` 테이블에 신규 행을 INSERT하는 경우, **THE SYSTEM SHALL** 해당 `admin_user_id` 에 해당하는 관리자의 WebSocket 세션이 열려 있으면 `/user/queue/notifications` 로 `NotificationPushPayload` 를 즉시(동기 이벤트 발행 후 최대 100ms 이내) 전송한다.

- 대상 관리자가 현재 연결되어 있지 않은 경우 메시지를 전송하지 않는다 (비연결 관리자에게 메시지 버퍼링 불필요).
- `unreadCount` 는 INSERT 직후 `admin_notification` 에서 COUNT 조회하여 포함한다.
- 이벤트 발행 방식: `AdminNotificationService` 가 알림 저장 후 `ApplicationEventPublisher` 로 `AdminNotificationCreatedEvent` 를 발행하고, `NotificationWebSocketHandler` 가 구독·중계한다.

### REQ-NWS-003 — 프론트엔드 헤더 배지 실시간 갱신

**WHEN** 프론트엔드가 WebSocket 세션을 통해 `NOTIFICATION` 타입 메시지를 수신하는 경우, **THE SYSTEM SHALL** 헤더 배지의 미읽음 수를 수신된 `unreadCount` 값으로 즉시 갱신한다.

- 기존 `AppHeader.vue` 의 `setInterval(30000)` 폴링 코드를 제거하고 `useNotificationWs` composable 로 교체한다.
- `CONNECTED` 메시지 수신 시에도 `unreadCount` 로 배지를 초기화한다.

### REQ-NWS-004 — 폴백 폴링

**WHEN** WebSocket 연결이 끊기거나 SockJS 핸드셰이크에 실패하는 경우, **THE SYSTEM SHALL** `GET /api/v1/admin/notifications/unread-count` 를 30초 간격으로 폴링하여 헤더 배지를 갱신하고, WebSocket 재연결을 백그라운드에서 시도한다.

- 재연결 간격: 첫 실패 후 5초, 이후 10초·30초·60초 지수 백오프(최대 60초).
- WebSocket 재연결 성공 시 폴링을 중단하고 WebSocket 구독으로 복귀한다.
- 사용자에게 연결 끊김 상태를 UI 인디케이터로 표시하지 않는다 (폴백이 투명하게 동작).

### REQ-NWS-005 — 알림 센터 화면 신규 알림 표시

**WHEN** 관리자가 `/admin/notifications` 화면을 열람 중에 `NOTIFICATION` 타입 메시지를 수신하는 경우, **THE SYSTEM SHALL** 알림 목록 상단에 신규 알림을 즉시 추가하거나 "새 알림 N건이 도착했습니다" 토스트를 표시한다.

- 신규 알림 추가 방식: 수신된 `NotificationPushPayload` 의 메타데이터를 목록에 즉시 prepend 한다.
- 알림 센터 화면이 닫혀 있을 때 수신된 알림은 다음 화면 진입 시 REST API 목록 조회로 표시된다.

### REQ-NWS-006 — 인증 세션 만료 처리

**WHEN** JWT 토큰이 WebSocket 세션 유지 중 만료되는 경우, **THE SYSTEM SHALL** 서버가 `ERROR` STOMP 프레임을 전송하고 세션을 종료하며, 클라이언트는 로그인 페이지로 리디렉션 없이 폴백 폴링으로 전환한다.

- 클라이언트는 401 STOMP ERROR 를 수신 시 토큰 갱신(refresh) 을 시도하고, 갱신 성공 시 새 토큰으로 재연결한다.
- 갱신 실패(refresh token 만료) 시 기존 로그인 만료 처리 흐름(로그인 페이지 리디렉션)을 따른다.

---

## 7. 인수 조건

### AC-NWS-001 — WebSocket 연결 및 인증

- 유효한 JWT 를 가진 관리자가 `/ws/notifications` 에 연결하면 STOMP `CONNECTED` 프레임을 수신한다.
- 만료된 JWT 로 연결 시도 시 HTTP 401 응답으로 핸드셰이크가 거부된다.
- SUPER_ADMIN 또는 CONTENT_ADMIN 이 아닌 사용자의 연결은 HTTP 403 으로 거부된다.
- SockJS 환경에서도 동일하게 연결이 수립되고 `CONNECTED` 메시지가 수신된다.
- `CONNECTED` 메시지에 `unreadCount` 값이 포함되어 있다.

### AC-NWS-002 — 신규 알림 즉시 수신

- 관리자 A 가 WebSocket 구독 중일 때, 시스템이 관리자 A 에게 알림을 INSERT 하면 1초 이내에 `/user/queue/notifications` 에서 `NOTIFICATION` 메시지가 수신된다.
- 수신 메시지의 `id`, `severity`, `title`, `refType`, `refId`, `createdAt`, `unreadCount` 필드가 모두 올바른 값을 가진다.
- 관리자 B 의 알림이 INSERT 되어도 관리자 A 의 구독에는 메시지가 전달되지 않는다.

### AC-NWS-003 — 헤더 배지 실시간 갱신

- WebSocket 구독 중 `NOTIFICATION` 메시지 수신 시 헤더 배지의 숫자가 `unreadCount` 값으로 즉시 변경된다.
- `CONNECTED` 메시지 수신 시 헤더 배지가 `unreadCount` 로 초기화된다.
- 기존 30초 폴링 코드(`setInterval`)가 `AppHeader.vue` 에 더 이상 존재하지 않는다.

### AC-NWS-004 — 폴백 폴링 동작

- WebSocket 연결 실패 시 30초 폴링이 자동으로 시작되고 헤더 배지가 갱신된다.
- 재연결 성공 후 폴링 인터벌이 제거된다.
- 네트워크를 끊었다 복구하는 시나리오에서 WebSocket 재연결 후 30초 폴링이 중단됨을 확인한다.

### AC-NWS-005 — 알림 센터 화면 실시간 업데이트

- `/admin/notifications` 화면 열람 중 서버에서 신규 알림이 생성되면 1초 이내에 목록 최상단에 해당 알림이 나타난다.
- 화면 진입 시 초기 목록은 REST API `GET /api/v1/admin/notifications` 로 로드된다.
- WebSocket 실시간 수신과 REST API 초기 로드가 중복 없이 동작한다.

### AC-NWS-006 — JWT 만료 처리

- WebSocket 세션 유지 중 JWT 가 만료되면 클라이언트가 STOMP ERROR 프레임을 수신한다.
- 토큰 갱신 성공 시 새 토큰으로 WebSocket 재연결이 이루어진다.
- 토큰 갱신 실패 시 로그인 페이지로 리디렉션된다.

---

## 8. 기술 접근법

### 8.1 Spring WebSocket + STOMP

```
의존성 추가:
  - spring-boot-starter-websocket (Spring Boot 3.3 포함, 별도 추가 없음)

설정 클래스:
  - WebSocketConfig implements WebSocketMessageBrokerConfigurer
    - registerStompEndpoints: /ws/notifications (withSockJS)
    - configureMessageBroker: enableSimpleBroker("/topic", "/user")
                               setApplicationDestinationPrefixes("/app")
                               setUserDestinationPrefix("/user")

인증:
  - ChannelInterceptor 구현체 → CONNECT 프레임에서 Authorization 헤더 추출
  - JwtTokenProvider.validateToken() 재사용
  - StompHeaderAccessor.setUser() 로 Principal 주입

알림 발행:
  - AdminNotificationService.createNotification() 내 ApplicationEventPublisher.publishEvent()
  - @EventListener AdminNotificationCreatedEvent → SimpMessagingTemplate.convertAndSendToUser()
```

### 8.2 Vue 3 Composable — `useNotificationWs`

```
위치: src/composables/useNotificationWs.ts

책임:
  - SockJS + STOMP Client 초기화 및 연결
  - /user/queue/notifications 구독
  - 수신 메시지 처리: unreadCount ref 갱신, 알림 센터 스토어 업데이트
  - 연결 끊김 감지 → 폴백 폴링 시작
  - 재연결 성공 → 폴백 폴링 중단
  - JWT 갱신 후 재연결 처리

사용:
  - AppHeader.vue: setInterval 제거, useNotificationWs() 호출
  - NotificationCenterView.vue: 신규 알림 prepend 처리
```

### 8.3 의존성

| 라이브러리 | 버전 | 용도 |
|---|---|---|
| `@stomp/stompjs` | ^7.0.0 | STOMP 프로토콜 클라이언트 |
| `sockjs-client` | ^1.6.1 | SockJS 폴백 |
| `spring-boot-starter-websocket` | (Spring Boot 3.3 관리) | 서버 WebSocket + STOMP |

### 8.4 고려사항

- **동시 접속**: 관리자 동시 접속자 최대 50명 기준. Simple Broker(인메모리) 로 충분. RabbitMQ/Redis 브로커는 본 SPEC 범위 외.
- **개인 큐 보안**: `convertAndSendToUser()` 는 Principal 기반으로 큐를 격리하므로 타 관리자 메시지 노출 불가.
- **클라스터 배포**: 현재 단일 인스턴스 기준. 멀티 인스턴스 시 외부 브로커(RabbitMQ STOMP) 필요 — 별도 SPEC 처리.
- **Heartbeat**: STOMP heartbeat 기본값(10000ms/10000ms) 적용. 네트워크 중간 장치 타임아웃 고려하여 클라이언트 heartbeat 15000ms 설정 권장.

---

## 9. 영향 파일 목록

### 9.1 신규 생성 (백엔드)

| 파일 | 설명 |
|---|---|
| `src/main/java/.../config/WebSocketConfig.java` | STOMP 엔드포인트·브로커 설정 |
| `src/main/java/.../config/WebSocketSecurityConfig.java` | WebSocket 채널 인터셉터, JWT 검증 |
| `src/main/java/.../notification/event/AdminNotificationCreatedEvent.java` | 알림 생성 이벤트 DTO |
| `src/main/java/.../notification/handler/NotificationWebSocketHandler.java` | 이벤트 구독 → STOMP 발행 |
| `src/main/java/.../notification/dto/NotificationPushPayload.java` | WebSocket 푸시 메시지 DTO |

### 9.2 수정 (백엔드)

| 파일 | 변경 내용 |
|---|---|
| `src/main/java/.../notification/service/AdminNotificationService.java` | `createNotification()` 에 `ApplicationEventPublisher.publishEvent()` 추가 |
| `build.gradle` | (spring-boot-starter-websocket 이미 포함 여부 확인 후 불필요 시 변경 없음) |

### 9.3 신규 생성 (프론트엔드)

| 파일 | 설명 |
|---|---|
| `src/composables/useNotificationWs.ts` | WebSocket 연결·구독·폴백 composable |
| `src/types/notification-ws.ts` | `NotificationPushPayload`, `ConnectionAckPayload` 타입 정의 |

### 9.4 수정 (프론트엔드)

| 파일 | 변경 내용 |
|---|---|
| `src/components/layout/AppHeader.vue` | `setInterval` 폴링 제거 → `useNotificationWs()` 구독으로 교체 |
| `src/views/admin/notification/NotificationCenterView.vue` | `useNotificationWs()` 연동, 신규 알림 prepend 처리 추가 |
| `package.json` | `@stomp/stompjs`, `sockjs-client` 의존성 추가 |

### 9.5 테스트

| 파일 | 설명 |
|---|---|
| `src/test/java/.../notification/WebSocketIntegrationTest.java` | WebSocket 연결·인증·푸시·격리 통합 테스트 |
| `src/test/java/.../notification/handler/NotificationWebSocketHandlerTest.java` | 이벤트 → STOMP 발행 단위 테스트 |
