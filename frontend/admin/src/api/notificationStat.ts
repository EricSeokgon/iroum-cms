// 알림 발송 통계 API 래퍼 — SPEC-CMS-NOTIFICATION-STAT-001
import { apiClient } from '@iroum/shared/api/client'

// @MX:ANCHOR: [AUTO] notificationStatApi — NotificationStatPanel + notificationStatStore에서 공통 참조
// @MX:REASON: fan_in >= 3: 패널 컴포넌트와 스토어 액션(summary/category/trend/errors/resend)에서 공통 호출

// ── 응답 타입 ─────────────────────────────────────────────────────────────────
export interface NotificationStatSummary {
  todayDispatched: number
  todayReadRate: number // 0-100 (백분율)
  todayUnread: number
  todayErrors: number
  sevenDayDispatched: number
  sevenDayReadRate: number
  sevenDayUnread: number
  sevenDayErrors: number
  thirtyDayDispatched: number
  thirtyDayReadRate: number
  thirtyDayUnread: number
  thirtyDayErrors: number
}

export interface CategoryStat {
  type: string
  dispatched: number
  readCount: number
}

export interface DailyTrendPoint {
  date: string // YYYY-MM-DD
  dispatched: number
  readCount: number
}

export interface FailedNotificationDto {
  id: number
  userId: number
  type: string
  title: string
  deliveryStatus: string // FAILED | PENDING
  createdAt: string
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// baseURL이 '/api/v1'이므로 여기서는 '/admin/...'만 사용
const BASE = '/admin/notifications/stats'

export const notificationStatApi = {
  // 오늘/7일/30일 요약 통계
  getSummary(): Promise<{ data: NotificationStatSummary }> {
    return apiClient.get(`${BASE}/summary`)
  },

  // 카테고리(알림 타입)별 발송/읽음 집계
  getByCategory(from?: string, to?: string): Promise<{ data: CategoryStat[] }> {
    return apiClient.get(`${BASE}/by-category`, { params: { from, to } })
  },

  // 일자별 시계열 (기본 30일)
  getDailyTrend(from?: string, to?: string): Promise<{ data: DailyTrendPoint[] }> {
    return apiClient.get(`${BASE}/daily-trend`, { params: { from, to } })
  },

  // 실패/대기 알림 목록 (페이지네이션)
  getErrors(page = 0, size = 20): Promise<{ data: PageResponse<FailedNotificationDto> }> {
    return apiClient.get(`${BASE}/errors`, { params: { page, size } })
  },

  // 재발송 (SENT 처리)
  resend(id: number): Promise<{ data: unknown }> {
    return apiClient.patch(`${BASE}/errors/${id}/resend`)
  },
}
