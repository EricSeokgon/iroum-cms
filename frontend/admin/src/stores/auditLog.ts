// SPEC-CMS-AUDIT-LOG-VIEW-001 — 통합 감사 로그 Pinia 스토어
// @MX:ANCHOR: [AUTO] useAuditLogStore — AuditLogView, 단위 테스트, (향후 대시보드 위젯)에서 공유 상태
// @MX:REASON: fan_in >= 3: 감사 로그 화면 상태 + 필터 + CSV 내보내기 단일 진실 공급원
import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import {
  auditLogs,
  type AuditLogResponse,
  type AuditLogFilter,
  type AuditAction,
  type AuditSeverity,
  type AuditResult,
} from '@/api/system'

/** REQ-AL-002 — 화면 필터 상태(다중 선택 + 라디오 + 날짜 + 행위자). */
export interface AuditLogFilterState {
  action: AuditAction[]
  severity: AuditSeverity[]
  result: AuditResult | ''
  fromTime: string
  toTime: string
  actorId: number | null
}

function defaultFilters(): AuditLogFilterState {
  return {
    action: [],
    severity: [],
    result: '',
    fromTime: '',
    toTime: '',
    actorId: null,
  }
}

export const useAuditLogStore = defineStore('auditLog', () => {
  // ── 상태 ─────────────────────────────────────────────────────────────────
  const logs = ref<AuditLogResponse[]>([])
  const total = ref(0)
  const page = ref(1) // 백엔드는 1-based
  const size = ref(50)
  const loading = ref(false)
  const error = ref<string | null>(null)

  /** REQ-AL-003 — CRITICAL 알림 패널용 목록 + 세션 닫힘 플래그. */
  const criticalLogs = ref<AuditLogResponse[]>([])
  const criticalDismissed = ref(false)

  const filters = reactive<AuditLogFilterState>(defaultFilters())

  // ── 내부 헬퍼 ────────────────────────────────────────────────────────────
  /**
   * 화면 필터 상태를 백엔드 파라미터로 변환한다.
   * action/severity는 다중 선택 지원 — 빈 배열이면 전달하지 않는다.
   */
  function buildFilter(overrides: Partial<AuditLogFilter> = {}): AuditLogFilter {
    return {
      fromTime: filters.fromTime || undefined,
      toTime: filters.toTime || undefined,
      actor_id: filters.actorId ?? undefined,
      action: filters.action.length > 0 ? filters.action.join(',') : undefined,
      severity: filters.severity.length > 0 ? filters.severity.join(',') : undefined,
      result: filters.result || undefined,
      page: page.value,
      size: size.value,
      ...overrides,
    }
  }

  function setError(e: unknown, fallback: string): void {
    error.value = e instanceof Error ? e.message : fallback
  }

  // ── 액션 ─────────────────────────────────────────────────────────────────
  /** REQ-AL-001 / AC-AL-001 — 목록 조회. */
  async function fetchLogs(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await auditLogs.search(buildFilter())
      logs.value = res.data.items
      total.value = res.data.total
    } catch (e) {
      // REQ-AL-011 — error state 만 설정, 토스트는 뷰의 책임
      setError(e, '감사 로그를 불러오지 못했습니다.')
    } finally {
      loading.value = false
    }
  }

  /** REQ-AL-003 — CRITICAL 이벤트 조회(실패는 조용히 무시). */
  async function fetchCritical(): Promise<void> {
    try {
      const res = await auditLogs.critical()
      criticalLogs.value = res.data
    } catch {
      // CRITICAL 패널은 보조 정보 — 실패 시 패널만 비표시
    }
  }

  /** REQ-AL-003 — 세션 동안 CRITICAL 패널 닫기. */
  function dismissCritical(): void {
    criticalDismissed.value = true
  }

  /** REQ-AL-002 — 필터 적용 후 첫 페이지부터 재조회. */
  async function applyFilter(partial: Partial<AuditLogFilterState>): Promise<void> {
    Object.assign(filters, partial)
    page.value = 1
    await fetchLogs()
  }

  /** REQ-AL-010 / AC-AL-010 — 필터 초기화 후 재조회. */
  async function resetFilter(): Promise<void> {
    Object.assign(filters, defaultFilters())
    page.value = 1
    await fetchLogs()
  }

  /** REQ-AL-006 — 페이지/사이즈 변경 후 재조회. */
  async function changePage(p: number): Promise<void> {
    page.value = p
    await fetchLogs()
  }

  async function changeSize(s: number): Promise<void> {
    size.value = s
    page.value = 1
    await fetchLogs()
  }

  /** REQ-AL-005 / AC-AL-005 — 현재 필터로 CSV 내보내기. */
  async function exportCsv(): Promise<void> {
    const { page: _p, size: _s, ...filterParams } = buildFilter()
    void _p
    void _s
    const res = await auditLogs.exportCsv(filterParams)
    const url = URL.createObjectURL(res.data as Blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `audit-logs-${Date.now()}.csv`
    a.click()
    URL.revokeObjectURL(url)
  }

  return {
    // state
    logs,
    total,
    page,
    size,
    loading,
    error,
    criticalLogs,
    criticalDismissed,
    filters,
    // actions
    fetchLogs,
    fetchCritical,
    dismissCritical,
    applyFilter,
    resetFilter,
    changePage,
    changeSize,
    exportCsv,
  }
})
