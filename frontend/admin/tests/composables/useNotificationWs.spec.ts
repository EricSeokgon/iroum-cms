// SPEC-CMS-NOTIFICATION-WS-001 — useNotificationWs composable 단위 테스트
// REQ-NWS-003(헤더 배지 실시간 갱신) / REQ-NWS-004(폴백 폴링) / REQ-NWS-005(composable 생명주기)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

import { useNotificationWs } from '@/composables/useNotificationWs'
import { useNotificationCenterStore } from '@/stores/notificationCenter'
import type { StompClientLike, StompClientFactory } from '@/composables/useNotificationWs'

// 가짜 STOMP 클라이언트 — connect/subscribe/disconnect 를 수동 제어한다.
class FakeStompClient implements StompClientLike {
  onConnect: (() => void) | null = null
  onWebSocketClose: (() => void) | null = null
  onStompError: (() => void) | null = null
  activated = false
  deactivated = false
  subscriptions: Record<string, (msg: { body: string }) => void> = {}

  activate(): void {
    this.activated = true
  }

  deactivate(): Promise<void> {
    this.deactivated = true
    return Promise.resolve()
  }

  subscribe(destination: string, cb: (msg: { body: string }) => void): { unsubscribe(): void } {
    this.subscriptions[destination] = cb
    return { unsubscribe: () => { delete this.subscriptions[destination] } }
  }

  // 테스트 헬퍼: 연결 성공 시뮬레이션
  simulateConnect(): void {
    this.onConnect?.()
  }

  // 테스트 헬퍼: 특정 토픽으로 메시지 푸시
  push(destination: string, payload: unknown): void {
    this.subscriptions[destination]?.({ body: JSON.stringify(payload) })
  }

  // 테스트 헬퍼: 연결 끊김 시뮬레이션
  simulateClose(): void {
    this.onWebSocketClose?.()
  }
}

let fakeClient: FakeStompClient
const factory: StompClientFactory = (handlers) => {
  fakeClient = new FakeStompClient()
  fakeClient.onConnect = handlers.onConnect ?? null
  fakeClient.onWebSocketClose = handlers.onWebSocketClose ?? null
  fakeClient.onStompError = handlers.onStompError ?? null
  return fakeClient
}

// 인증 스토어 모킹 — 토큰/userId 제공
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    getToken: () => 'test-jwt-token',
    accessToken: 'test-jwt-token',
    user: { id: 42, username: 'admin', roleCodes: ['ADMIN'] },
  }),
}))

function createHost() {
  let api: ReturnType<typeof useNotificationWs> | null = null
  const Host = defineComponent({
    setup() {
      api = useNotificationWs({ clientFactory: factory, pollIntervalMs: 30_000 })
      return () => h('div')
    },
  })
  const wrapper = mount(Host)
  return {
    wrapper,
    get api() {
      if (!api) throw new Error('composable not initialized')
      return api
    },
  }
}

describe('useNotificationWs — SPEC-CMS-NOTIFICATION-WS-001', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('마운트 시 STOMP 클라이언트를 활성화한다 (REQ-NWS-005)', async () => {
    const { wrapper } = createHost()
    await wrapper.vm.$nextTick()
    expect(fakeClient.activated).toBe(true)
    wrapper.unmount()
  })

  it('CONNECTED ack 수신 시 unreadCount 를 갱신한다 (REQ-NWS-003)', async () => {
    const { wrapper } = createHost()
    await wrapper.vm.$nextTick()
    const store = useNotificationCenterStore()

    fakeClient.simulateConnect()
    await wrapper.vm.$nextTick()
    fakeClient.push('/topic/notifications/42/ack', { type: 'CONNECTED', unreadCount: 3 })

    expect(store.unreadCount).toBe(3)
    wrapper.unmount()
  })

  it('NOTIFICATION 메시지 수신 시 unreadCount 를 즉시 갱신한다 (REQ-NWS-003)', async () => {
    const { wrapper } = createHost()
    await wrapper.vm.$nextTick()
    const store = useNotificationCenterStore()

    fakeClient.simulateConnect()
    await wrapper.vm.$nextTick()
    fakeClient.push('/topic/notifications/42', {
      type: 'NOTIFICATION',
      id: 123,
      notificationType: 'POST_APPROVAL_REQUEST',
      severity: 'INFO',
      title: '승인 요청',
      unreadCount: 5,
    })

    expect(store.unreadCount).toBe(5)
    wrapper.unmount()
  })

  it('연결 끊김 시 30초 폴백 폴링을 시작한다 (REQ-NWS-004)', async () => {
    const { wrapper } = createHost()
    await wrapper.vm.$nextTick()
    const store = useNotificationCenterStore()
    const fetchSpy = vi.spyOn(store, 'fetchUnreadCount').mockResolvedValue(undefined)

    // 먼저 연결되었다가
    fakeClient.simulateConnect()
    await wrapper.vm.$nextTick()
    // 끊김 발생 → 폴백 폴링 시작
    fakeClient.simulateClose()
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(30_000)
    expect(fetchSpy).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('재연결 성공 시 폴백 폴링을 중단한다 (REQ-NWS-004)', async () => {
    const { wrapper } = createHost()
    await wrapper.vm.$nextTick()
    const store = useNotificationCenterStore()
    const fetchSpy = vi.spyOn(store, 'fetchUnreadCount').mockResolvedValue(undefined)

    fakeClient.simulateConnect()
    await wrapper.vm.$nextTick()
    fakeClient.simulateClose()
    await wrapper.vm.$nextTick()

    // 폴링 1회 발생
    await vi.advanceTimersByTimeAsync(30_000)
    const callsAfterClose = fetchSpy.mock.calls.length
    expect(callsAfterClose).toBeGreaterThanOrEqual(1)

    // 재연결 성공 → 폴링 중단
    fakeClient.simulateConnect()
    await wrapper.vm.$nextTick()
    fetchSpy.mockClear()

    await vi.advanceTimersByTimeAsync(60_000)
    expect(fetchSpy).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('언마운트 시 클라이언트를 비활성화하고 폴링을 정리한다 (REQ-NWS-005)', async () => {
    const { wrapper } = createHost()
    await wrapper.vm.$nextTick()
    const store = useNotificationCenterStore()
    const fetchSpy = vi.spyOn(store, 'fetchUnreadCount').mockResolvedValue(undefined)

    fakeClient.simulateConnect()
    await wrapper.vm.$nextTick()
    fakeClient.simulateClose()
    await wrapper.vm.$nextTick()

    wrapper.unmount()
    expect(fakeClient.deactivated).toBe(true)

    fetchSpy.mockClear()
    await vi.advanceTimersByTimeAsync(60_000)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('connected ref 가 연결 상태를 반영한다 (REQ-NWS-005)', async () => {
    const { wrapper, api } = createHost()
    await wrapper.vm.$nextTick()

    expect(api.connected.value).toBe(false)
    fakeClient.simulateConnect()
    await wrapper.vm.$nextTick()
    expect(api.connected.value).toBe(true)

    fakeClient.simulateClose()
    await wrapper.vm.$nextTick()
    expect(api.connected.value).toBe(false)
    wrapper.unmount()
  })
})
