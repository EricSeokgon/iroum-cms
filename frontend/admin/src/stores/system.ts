// 시스템 관리 Pinia 스토어 — SPEC-CMS-005 Bundle D
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboard, codes } from '@/api/system'
import type { DashboardKpiResponse, TrendItemResponse, TopPageResponse, CodeResponse } from '@/api/system'

// @MX:ANCHOR: [AUTO] useDashboardStore — SystemDashboardView, AdminLayout(점검배너 조건)에서 참조
// @MX:REASON: fan_in >= 3: SystemDashboardView, 테스트, MaintenanceBanner 등에서 공통 참조

export const useDashboardStore = defineStore('dashboard', () => {
  const kpi = ref<DashboardKpiResponse | null>(null)
  const trends = ref<TrendItemResponse[]>([])
  const topPages = ref<TopPageResponse[]>([])
  const lastFetched = ref<Date | null>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function fetchAll(noCache = false): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const [kpiRes, trendsRes, topPagesRes] = await Promise.all([
        dashboard.kpi({ noCache }),
        dashboard.trends(30),
        dashboard.topPages('7d'),
      ])
      kpi.value = kpiRes.data
      trends.value = trendsRes.data
      topPages.value = topPagesRes.data
      lastFetched.value = new Date()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '데이터 조회 실패'
    } finally {
      loading.value = false
    }
  }

  function invalidate(): void {
    kpi.value = null
    trends.value = []
    topPages.value = []
    lastFetched.value = null
  }

  return { kpi, trends, topPages, lastFetched, loading, error, fetchAll, invalidate }
})

// ── 공통 코드 벌크 캐시 스토어 ─────────────────────────────────────────────────
export const useCodeCacheStore = defineStore('codeCache', () => {
  // Map<groupCode, Code[]>
  const cache = ref<Map<string, CodeResponse[]>>(new Map())
  const loading = ref(false)

  async function fetchBulk(groupCodes: string[]): Promise<void> {
    const missing = groupCodes.filter(g => !cache.value.has(g))
    if (missing.length === 0) return

    loading.value = true
    try {
      const res = await codes.bulk(missing)
      for (const [groupCode, codeList] of Object.entries(res.data)) {
        cache.value.set(groupCode, codeList)
      }
    } finally {
      loading.value = false
    }
  }

  function getGroup(groupCode: string): CodeResponse[] {
    return cache.value.get(groupCode) ?? []
  }

  function clear(): void {
    cache.value = new Map()
  }

  return { cache, loading, fetchBulk, getGroup, clear }
})
