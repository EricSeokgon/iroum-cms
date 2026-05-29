// SPEC-CMS-NOTIFICATION-CENTER-001 — 관리자 알림 Pinia 스토어
// @MX:NOTE: [AUTO] useNotificationCenterStore — 헤더 배지, NotificationCenterView, 폴링 composable 공유 상태
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import {
  adminNotificationApi,
  type AdminNotificationDto,
  type AdminNotificationSeverity,
  type AdminNotificationStatus,
  type NotificationListParams,
  type MarkAllReadRequest,
} from '@/api/adminNotifications'

export interface NotificationFilterState {
  status: AdminNotificationStatus[]
  severity: AdminNotificationSeverity[]
  type: string[]
  from: string | null
  to: string | null
}

const DEFAULT_FILTER: NotificationFilterState = {
  status: ['UNREAD', 'READ'],
  severity: [],
  type: [],
  from: null,
  to: null,
}

export const useNotificationCenterStore = defineStore('notificationCenter', () => {
  // ── 상태 ─────────────────────────────────────────────────────────────────
  const notifications = ref<AdminNotificationDto[]>([])
  const totalElements = ref(0)
  const totalPages = ref(0)
  const page = ref(0)
  const size = ref(20)
  const loading = ref(false)
  const error = ref<string | null>(null)

  /** REQ-NC-005/006 — 헤더 배지가 구독하는 미읽음 수. */
  const unreadCount = ref(0)

  const filter = ref<NotificationFilterState>({ ...DEFAULT_FILTER })

  // ── Getter ───────────────────────────────────────────────────────────────
  /** REQ-NC-006 — 99+ 표시용 헬퍼. */
  const unreadBadge = computed<string>(() => {
    const n = unreadCount.value
    if (n <= 0) return ''
    if (n >= 100) return '99+'
    return String(n)
  })

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 액션 ─────────────────────────────────────────────────────────────────
  function buildParams(overrides: Partial<NotificationListParams> = {}): NotificationListParams {
    const f = filter.value
    return {
      status: f.status.length > 0 ? f.status : undefined,
      severity: f.severity.length > 0 ? f.severity : undefined,
      type: f.type.length > 0 ? f.type : undefined,
      from: f.from ?? undefined,
      to: f.to ?? undefined,
      page: page.value,
      size: size.value,
      ...overrides,
    }
  }

  /** REQ-NC-001 — 목록 조회. */
  async function fetchNotifications(overrides: Partial<NotificationListParams> = {}): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await adminNotificationApi.list(buildParams(overrides))
      notifications.value = res.data.content
      totalElements.value = res.data.totalElements
      totalPages.value = res.data.totalPages
      page.value = res.data.page
      size.value = res.data.size
    } catch (e) {
      setError(e, '알림 목록을 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** REQ-NC-005 — 미읽음 수 갱신 (폴링·헤더 배지). */
  async function fetchUnreadCount(): Promise<void> {
    try {
      const res = await adminNotificationApi.getUnreadCount()
      unreadCount.value = res.data.unreadCount
    } catch {
      // REQ-NC-009 — 폴링 실패는 사용자 토스트 없음, 다음 폴링까지 대기
    }
  }

  /** REQ-NC-002 — 단건 읽음 처리 + 로컬 상태 반영. */
  async function markRead(id: number): Promise<void> {
    try {
      await adminNotificationApi.markRead(id)
      const target = notifications.value.find((n) => n.id === id)
      if (target && target.status === 'UNREAD') {
        target.status = 'READ'
        target.readAt = new Date().toISOString()
        unreadCount.value = Math.max(0, unreadCount.value - 1)
      }
    } catch (e) {
      setError(e, '읽음 처리에 실패했습니다.')
      throw e
    }
  }

  /** REQ-NC-003 — 일괄 읽음 처리. updatedCount 반환. */
  async function markAllRead(req: MarkAllReadRequest = {}): Promise<number> {
    try {
      const res = await adminNotificationApi.markAllRead(req)
      // 로컬 상태 갱신은 fetchNotifications 으로 재조회
      await fetchUnreadCount()
      return res.data.updatedCount
    } catch (e) {
      setError(e, '일괄 읽음 처리에 실패했습니다.')
      throw e
    }
  }

  /** REQ-NC-004 — 보관 처리. */
  async function archive(id: number): Promise<void> {
    try {
      await adminNotificationApi.archive(id)
      const target = notifications.value.find((n) => n.id === id)
      if (target) {
        const wasUnread = target.status === 'UNREAD'
        target.status = 'ARCHIVED'
        target.archivedAt = new Date().toISOString()
        if (wasUnread) {
          target.readAt = target.readAt ?? new Date().toISOString()
          unreadCount.value = Math.max(0, unreadCount.value - 1)
        }
      }
    } catch (e) {
      setError(e, '보관 처리에 실패했습니다.')
      throw e
    }
  }

  function setPage(p: number): void {
    page.value = p
  }

  function setSize(s: number): void {
    size.value = s
    page.value = 0
  }

  function setFilter(partial: Partial<NotificationFilterState>): void {
    filter.value = { ...filter.value, ...partial }
    page.value = 0
  }

  function resetFilter(): void {
    filter.value = { ...DEFAULT_FILTER }
    page.value = 0
  }

  return {
    // state
    notifications,
    totalElements,
    totalPages,
    page,
    size,
    loading,
    error,
    unreadCount,
    filter,
    // getters
    unreadBadge,
    // actions
    fetchNotifications,
    fetchUnreadCount,
    markRead,
    markAllRead,
    archive,
    setPage,
    setSize,
    setFilter,
    resetFilter,
  }
})
