// 알림 발송 통계 Pinia 스토어 — SPEC-CMS-NOTIFICATION-STAT-001
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationStatApi } from '@/api/notificationStat'
import type {
  NotificationStatSummary,
  CategoryStat,
  DailyTrendPoint,
  FailedNotificationDto,
  PageResponse,
} from '@/api/notificationStat'

// @MX:ANCHOR: [AUTO] useNotificationStatStore — NotificationStatPanel 및 향후 통계 뷰에서 참조
// @MX:REASON: fan_in >= 3: summary/category/trend/errors/resend 액션이 패널 + 관련 뷰에서 공통 사용

export const useNotificationStatStore = defineStore('notificationStat', () => {
  // ── 상태 ────────────────────────────────────────────────────────────────────
  const summary = ref<NotificationStatSummary | null>(null)
  const categoryStats = ref<CategoryStat[]>([])
  const dailyTrend = ref<DailyTrendPoint[]>([])
  const errors = ref<PageResponse<FailedNotificationDto> | null>(null)
  const loading = ref(false)
  const errorsLoading = ref(false)
  const error = ref<string | null>(null)

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 액션 ────────────────────────────────────────────────────────────────────
  async function loadSummary(): Promise<void> {
    try {
      const res = await notificationStatApi.getSummary()
      summary.value = res.data
    } catch (e) {
      setError(e, '요약 통계 조회 실패')
    }
  }

  async function loadByCategory(from?: string, to?: string): Promise<void> {
    try {
      const res = await notificationStatApi.getByCategory(from, to)
      categoryStats.value = res.data
    } catch (e) {
      setError(e, '카테고리 통계 조회 실패')
    }
  }

  async function loadDailyTrend(from?: string, to?: string): Promise<void> {
    try {
      const res = await notificationStatApi.getDailyTrend(from, to)
      dailyTrend.value = res.data
    } catch (e) {
      setError(e, '일자별 추이 조회 실패')
    }
  }

  async function loadErrors(page = 0, size = 20): Promise<void> {
    errorsLoading.value = true
    try {
      const res = await notificationStatApi.getErrors(page, size)
      errors.value = res.data
    } catch (e) {
      setError(e, '실패 알림 목록 조회 실패')
    } finally {
      errorsLoading.value = false
    }
  }

  async function resend(id: number): Promise<void> {
    await notificationStatApi.resend(id)
    // 현재 페이지 갱신 (재발송 후 목록에서 제외되도록 재조회)
    const currentPage = errors.value?.page ?? 0
    const currentSize = errors.value?.size ?? 20
    await loadErrors(currentPage, currentSize)
  }

  // summary + category + trend 병렬 로드
  async function loadAll(from?: string, to?: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      // @MX:WARN: [AUTO] Promise.all 병렬 호출 — 개별 실패가 catch에서 setError로 누적됨
      // @MX:REASON: 3개 통계 API를 병렬 호출하며, 일부 실패해도 나머지 결과는 표시. 에러는 error 상태에 기록.
      await Promise.all([
        loadSummary(),
        loadByCategory(from, to),
        loadDailyTrend(from, to),
        loadErrors(0, 20),
      ])
    } finally {
      loading.value = false
    }
  }

  return {
    // 상태
    summary,
    categoryStats,
    dailyTrend,
    errors,
    loading,
    errorsLoading,
    error,
    // 액션
    loadSummary,
    loadByCategory,
    loadDailyTrend,
    loadErrors,
    resend,
    loadAll,
  }
})
