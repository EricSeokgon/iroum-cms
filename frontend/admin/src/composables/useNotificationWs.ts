// SPEC-CMS-NOTIFICATION-WS-001 — 관리자 알림 WebSocket 실시간 푸시 composable
//
// 책임(REQ-NWS-003/004/005):
//   - SockJS + STOMP 클라이언트 생성·연결 (관리자 JWT 핸드셰이크 헤더)
//   - /topic/notifications/{userId}, /topic/notifications/{userId}/ack 구독
//   - 수신 메시지로 notificationCenter 스토어의 unreadCount 즉시 갱신
//   - 연결 끊김 시 30초 폴백 폴링 시작, 재연결 성공 시 폴링 중단
//   - 언마운트 시 클라이언트 비활성화 + 폴링 정리
//
// @MX:NOTE: [AUTO] useNotificationWs — AppHeader/AdminLayout 의 30초 폴링을 대체하는 실시간 구독 진입점
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import { Client as StompClient } from '@stomp/stompjs'
import SockJS from 'sockjs-client'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import { useAuthStore } from '@/stores/auth'

/** 폴백 폴링 기본 주기(REQ-NWS-004). */
const DEFAULT_POLL_INTERVAL_MS = 30_000

/**
 * STOMP 클라이언트 추상화 — 테스트에서 가짜 구현으로 주입 가능하게 최소 인터페이스만 정의한다.
 */
export interface StompClientLike {
  activate(): void
  deactivate(): Promise<void> | void
  subscribe(
    destination: string,
    callback: (message: { body: string }) => void,
  ): { unsubscribe(): void }
}

/** 클라이언트 생성 시 등록할 생명주기 핸들러. */
export interface StompClientHandlers {
  onConnect?: () => void
  onWebSocketClose?: () => void
  onStompError?: () => void
}

/** STOMP 클라이언트 팩토리 — 운영은 @stomp/stompjs Client, 테스트는 Fake 주입. */
export type StompClientFactory = (handlers: StompClientHandlers) => StompClientLike

export interface UseNotificationWsOptions {
  /** 테스트용 클라이언트 팩토리. 미지정 시 SockJS+STOMP 운영 구현 사용. */
  clientFactory?: StompClientFactory
  /** 폴백 폴링 주기(ms). 기본 30초. */
  pollIntervalMs?: number
}

export interface UseNotificationWsReturn {
  /** 현재 WebSocket 연결 여부. */
  connected: Ref<boolean>
}

/** ack/알림 페이로드 공통 형태(부분). */
interface IncomingMessage {
  type?: string
  unreadCount?: number
}

/**
 * 운영용 SockJS + STOMP 클라이언트 팩토리.
 *
 * <p>SPEC §5.1 — `/ws/notifications` 엔드포인트에 SockJS 로 연결하고,
 * CONNECT 프레임에 `Authorization: Bearer <JWT>` 헤더를 실어 핸드셰이크 인증을 통과한다.
 * 자동 재연결(reconnectDelay)로 끊김 시 백그라운드 재연결을 시도한다(REQ-NWS-004).
 */
function createDefaultClient(handlers: StompClientHandlers): StompClientLike {
  const auth = useAuthStore()
  const token = auth.getToken() ?? ''

  const client = new StompClient({
    webSocketFactory: () => new SockJS('/ws/notifications') as unknown as WebSocket,
    connectHeaders: { Authorization: `Bearer ${token}` },
    reconnectDelay: 5_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 15_000,
  })
  client.onConnect = () => handlers.onConnect?.()
  client.onWebSocketClose = () => handlers.onWebSocketClose?.()
  client.onStompError = () => handlers.onStompError?.()
  return client as unknown as StompClientLike
}

/**
 * REQ-NWS-003/004/005 — WebSocket 실시간 알림 구독 + 폴백 폴링 composable.
 */
export function useNotificationWs(
  options: UseNotificationWsOptions = {},
): UseNotificationWsReturn {
  const store = useNotificationCenterStore()
  const auth = useAuthStore()
  const pollIntervalMs = options.pollIntervalMs ?? DEFAULT_POLL_INTERVAL_MS
  const clientFactory = options.clientFactory ?? createDefaultClient

  const connected = ref(false)
  let client: StompClientLike | null = null
  let pollTimer: ReturnType<typeof setInterval> | null = null
  let subscriptions: Array<{ unsubscribe(): void }> = []

  function startPolling(): void {
    if (pollTimer != null) return
    // 끊김 직후 즉시 1회 갱신 후 주기 폴링(REQ-NWS-004)
    void store.fetchUnreadCount()
    pollTimer = setInterval(() => {
      void store.fetchUnreadCount()
    }, pollIntervalMs)
  }

  function stopPolling(): void {
    if (pollTimer != null) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }

  function applyMessage(raw: string): void {
    try {
      const msg = JSON.parse(raw) as IncomingMessage
      if (typeof msg.unreadCount === 'number') {
        store.unreadCount = msg.unreadCount
      }
    } catch {
      // 파싱 실패 메시지는 무시(다음 폴링/메시지로 보정)
    }
  }

  function subscribeTopics(): void {
    const userId = auth.user?.id
    if (client == null || userId == null) return
    subscriptions.push(
      client.subscribe(`/topic/notifications/${userId}`, (m) => applyMessage(m.body)),
    )
    subscriptions.push(
      client.subscribe(`/topic/notifications/${userId}/ack`, (m) => applyMessage(m.body)),
    )
  }

  function clearSubscriptions(): void {
    subscriptions.forEach((s) => {
      try {
        s.unsubscribe()
      } catch {
        // 이미 끊긴 구독 해제 실패는 무시
      }
    })
    subscriptions = []
  }

  function onConnect(): void {
    connected.value = true
    // 재연결 성공 시 폴백 폴링 중단(REQ-NWS-004)
    stopPolling()
    clearSubscriptions()
    subscribeTopics()
  }

  function onDisconnect(): void {
    connected.value = false
    clearSubscriptions()
    // 끊김 시 30초 폴백 폴링 시작(REQ-NWS-004). 클라이언트 reconnectDelay 가 백그라운드 재연결 시도.
    startPolling()
  }

  onMounted(() => {
    client = clientFactory({
      onConnect,
      onWebSocketClose: onDisconnect,
      onStompError: onDisconnect,
    })
    client.activate()
  })

  onBeforeUnmount(() => {
    stopPolling()
    clearSubscriptions()
    if (client != null) {
      void client.deactivate()
      client = null
    }
  })

  return { connected }
}
