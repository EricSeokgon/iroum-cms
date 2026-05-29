// SPEC-CMS-NOTIFICATION-CENTER-001 — 관리자 알림 받은편지함 API 래퍼
// @MX:NOTE: [AUTO] adminNotificationApi — NotificationCenterView, 헤더 배지, 폴링 composable, store 에서 참조
import { apiClient } from '@iroum/shared/api/client'

// ── 타입 ───────────────────────────────────────────────────────────────────────

export type AdminNotificationSeverity = 'INFO' | 'WARN' | 'ERROR'
export type AdminNotificationStatus = 'UNREAD' | 'READ' | 'ARCHIVED'

export interface AdminNotificationDto {
  id: number
  adminUserId: number
  type: string
  severity: AdminNotificationSeverity
  title: string
  body?: string | null
  refType?: string | null
  refId?: number | null
  status: AdminNotificationStatus
  readAt?: string | null
  archivedAt?: string | null
  createdAt: string
}

/** 백엔드 kr.co.ircp.cms.domain.auth.dto.PageResponse 와 동일 형태. */
export interface AdminNotificationPage {
  content: AdminNotificationDto[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface NotificationListParams {
  /** 다중 선택 — undefined 시 백엔드 기본값 (UNREAD, READ) 사용. */
  status?: AdminNotificationStatus[]
  severity?: AdminNotificationSeverity[]
  type?: string[]
  /** ISO yyyy-MM-dd. */
  from?: string
  to?: string
  /** 0-base. */
  page?: number
  size?: number
}

export interface MarkAllReadRequest {
  severity?: AdminNotificationSeverity[]
  type?: string[]
}

export interface UnreadCountResponse {
  unreadCount: number
}

// ── API ────────────────────────────────────────────────────────────────────────

const BASE = '/admin/notifications'

export const adminNotificationApi = {
  /** REQ-NC-001 — 목록 조회. */
  list(params: NotificationListParams = {}) {
    return apiClient.get<AdminNotificationPage>(BASE, { params })
  },

  /** REQ-NC-002 — 단건 읽음 처리. */
  markRead(id: number) {
    return apiClient.patch<void>(`${BASE}/${id}/read`)
  },

  /** REQ-NC-003 — 일괄 읽음 처리. */
  markAllRead(req: MarkAllReadRequest = {}) {
    return apiClient.patch<{ updatedCount: number }>(`${BASE}/read-all`, req)
  },

  /** REQ-NC-004 — 보관 처리. */
  archive(id: number) {
    return apiClient.patch<void>(`${BASE}/${id}/archive`)
  },

  /** REQ-NC-005 — 미읽음 수. */
  getUnreadCount() {
    return apiClient.get<UnreadCountResponse>(`${BASE}/unread-count`)
  },
}
