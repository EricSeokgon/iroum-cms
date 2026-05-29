// SPEC-CMS-NOTIFICATION-CENTER-001 — useNotificationCenterStore 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/adminNotifications', () => ({
  adminNotificationApi: {
    list: vi.fn(),
    markRead: vi.fn(),
    markAllRead: vi.fn(),
    archive: vi.fn(),
    getUnreadCount: vi.fn(),
  },
}))

import { useNotificationCenterStore } from '@/stores/notificationCenter'
import { adminNotificationApi, type AdminNotificationDto } from '@/api/adminNotifications'

function sample(id: number, status: AdminNotificationDto['status'] = 'UNREAD'): AdminNotificationDto {
  return {
    id,
    adminUserId: 100,
    type: 'POST_APPROVAL_REQUEST',
    severity: 'INFO',
    title: `알림 ${id}`,
    body: '본문',
    refType: 'POST',
    refId: 42,
    status,
    readAt: null,
    archivedAt: null,
    createdAt: '2026-05-29T10:00:00Z',
  }
}

describe('useNotificationCenterStore (SPEC-CMS-NOTIFICATION-CENTER-001)', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('REQ-NC-001: fetchNotifications 가 PageResponse 를 state 에 채운다', async () => {
    vi.mocked(adminNotificationApi.list).mockResolvedValueOnce({
      data: {
        content: [sample(1), sample(2)],
        page: 0,
        size: 20,
        totalElements: 50,
        totalPages: 3,
      },
    } as never)

    const store = useNotificationCenterStore()
    await store.fetchNotifications()

    expect(store.notifications).toHaveLength(2)
    expect(store.totalElements).toBe(50)
    expect(store.totalPages).toBe(3)
  })

  it('REQ-NC-005: fetchUnreadCount 가 unreadCount 를 갱신한다', async () => {
    vi.mocked(adminNotificationApi.getUnreadCount).mockResolvedValueOnce({
      data: { unreadCount: 7 },
    } as never)

    const store = useNotificationCenterStore()
    await store.fetchUnreadCount()

    expect(store.unreadCount).toBe(7)
  })

  it('REQ-NC-006: unreadBadge 가 0/숫자/99+ 를 올바르게 표시한다', async () => {
    const store = useNotificationCenterStore()

    store.unreadCount = 0
    expect(store.unreadBadge).toBe('')

    store.unreadCount = 7
    expect(store.unreadBadge).toBe('7')

    store.unreadCount = 99
    expect(store.unreadBadge).toBe('99')

    store.unreadCount = 150
    expect(store.unreadBadge).toBe('99+')
  })

  it('REQ-NC-002: markRead 호출 시 로컬 상태가 READ 로 갱신되고 unreadCount 가 감소한다', async () => {
    vi.mocked(adminNotificationApi.list).mockResolvedValueOnce({
      data: {
        content: [sample(1, 'UNREAD'), sample(2, 'UNREAD')],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      },
    } as never)
    vi.mocked(adminNotificationApi.markRead).mockResolvedValueOnce({ data: undefined } as never)

    const store = useNotificationCenterStore()
    await store.fetchNotifications()
    store.unreadCount = 2

    await store.markRead(1)

    const updated = store.notifications.find((n) => n.id === 1)
    expect(updated?.status).toBe('READ')
    expect(updated?.readAt).not.toBeNull()
    expect(store.unreadCount).toBe(1)
  })

  it('REQ-NC-003: markAllRead 가 updatedCount 를 반환하고 unreadCount 를 재조회한다', async () => {
    vi.mocked(adminNotificationApi.markAllRead).mockResolvedValueOnce({
      data: { updatedCount: 30 },
    } as never)
    vi.mocked(adminNotificationApi.getUnreadCount).mockResolvedValueOnce({
      data: { unreadCount: 0 },
    } as never)

    const store = useNotificationCenterStore()
    const updated = await store.markAllRead({ severity: ['ERROR'] })

    expect(updated).toBe(30)
    expect(adminNotificationApi.markAllRead).toHaveBeenCalledWith({ severity: ['ERROR'] })
    expect(store.unreadCount).toBe(0)
  })

  it('REQ-NC-004: archive 시 status 가 ARCHIVED 로 변경되고 UNREAD 였다면 unreadCount 감소', async () => {
    vi.mocked(adminNotificationApi.list).mockResolvedValueOnce({
      data: {
        content: [sample(1, 'UNREAD')],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    } as never)
    vi.mocked(adminNotificationApi.archive).mockResolvedValueOnce({ data: undefined } as never)

    const store = useNotificationCenterStore()
    await store.fetchNotifications()
    store.unreadCount = 5

    await store.archive(1)

    const updated = store.notifications.find((n) => n.id === 1)
    expect(updated?.status).toBe('ARCHIVED')
    expect(updated?.archivedAt).not.toBeNull()
    expect(store.unreadCount).toBe(4)
  })

  it('REQ-NC-001: setFilter 호출 시 page 가 0 으로 리셋된다', () => {
    const store = useNotificationCenterStore()
    store.setPage(3)
    expect(store.page).toBe(3)

    store.setFilter({ severity: ['ERROR'] })
    expect(store.page).toBe(0)
    expect(store.filter.severity).toEqual(['ERROR'])
  })

  it('폴링 실패는 사용자 토스트 없이 무시된다 (REQ-NC-009)', async () => {
    vi.mocked(adminNotificationApi.getUnreadCount).mockRejectedValueOnce(new Error('network'))

    const store = useNotificationCenterStore()
    await store.fetchUnreadCount()

    expect(store.error).toBeNull() // 폴링 실패는 error state 에 기록하지 않음
    expect(store.unreadCount).toBe(0) // 기본값 유지
  })
})
