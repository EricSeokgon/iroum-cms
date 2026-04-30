// SystemDashboardView 단위 테스트 — SPEC-CMS-005 Bundle D REQ-SYS-001-D
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import ko from '@/locales/ko.json'
import SystemDashboardView from '@/views/system/SystemDashboardView.vue'
import { dashboard } from '@/api/system'
import type { DashboardKpiResponse, TrendItemResponse, TopPageResponse } from '@/api/system'

// ECharts / vue-echarts 모킹
vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    template: '<div class="mock-chart" />',
    props: ['option', 'autoresize'],
  },
}))
vi.mock('echarts/core', () => ({ use: vi.fn() }))
vi.mock('echarts/charts', () => ({ LineChart: {} }))
vi.mock('echarts/components', () => ({
  GridComponent: {},
  TooltipComponent: {},
  LegendComponent: {},
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

vi.mock('@/api/system', () => ({
  dashboard: {
    kpi: vi.fn(),
    trends: vi.fn(),
    topPages: vi.fn(),
  },
  stats: {},
  accessLogs: {},
  codeGroups: {},
  codes: {},
  settings: {},
  maintenance: {},
  auditLogs: {},
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

const mockKpi: DashboardKpiResponse = {
  today_visits: 1500,
  today_unique: 900,
  today_page_views: 4200,
  today_signups: 12,
  error_rate_24h: 0.01,
  avg_response_ms_24h: 250,
  locked_accounts: 0,
  audit_log_24h_count: 100,
  audit_log_critical_24h_count: 0,
  health_status: 'HEALTHY',
}

const mockTrends: TrendItemResponse[] = [
  { date: '2026-04-29', visits: 1200, page_views: 3600, errors: 10 },
]

const mockTopPages: TopPageResponse[] = [
  { rank: 1, page_url: '/', views: 500, avg_response_ms: 120, error_rate: 0.005 },
]

describe('SystemDashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dashboard.kpi).mockResolvedValue({ data: mockKpi } as never)
    vi.mocked(dashboard.trends).mockResolvedValue({ data: mockTrends } as never)
    vi.mocked(dashboard.topPages).mockResolvedValue({ data: mockTopPages } as never)
  })

  it('마운트 시 fetchAll이 호출된다', async () => {
    mount(SystemDashboardView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia({ stubActions: false })] },
    })
    await flushPromises()
    expect(dashboard.kpi).toHaveBeenCalledOnce()
    expect(dashboard.trends).toHaveBeenCalledOnce()
    expect(dashboard.topPages).toHaveBeenCalledOnce()
  })

  it('KPI 카드 10개가 렌더링된다', async () => {
    const wrapper = mount(SystemDashboardView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia({ stubActions: false })] },
    })
    await flushPromises()
    // KpiCard는 role="region"
    const cards = wrapper.findAll('[role="region"]')
    expect(cards.length).toBe(10)
  })

  it('새로고침 버튼 클릭 시 dashboard.kpi가 재호출된다', async () => {
    const wrapper = mount(SystemDashboardView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia({ stubActions: false })] },
    })
    await flushPromises()
    vi.mocked(dashboard.kpi).mockClear()

    const btn = wrapper.find('button')
    await btn.trigger('click')
    await flushPromises()
    expect(dashboard.kpi).toHaveBeenCalledOnce()
  })

  it('noCache ref를 true로 설정 후 reload 시 noCache: true로 kpi 호출', async () => {
    const wrapper = mount(SystemDashboardView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia({ stubActions: false })] },
    })
    await flushPromises()
    vi.mocked(dashboard.kpi).mockClear()

    // noCache 플래그 설정
    const vm = wrapper.vm as { noCache: { value: boolean }; reload: () => Promise<void> }
    ;(vm as unknown as { noCache: boolean }).noCache = true
    await flushPromises()

    // reload 직접 호출
    await (wrapper.vm as unknown as { reload: () => Promise<void> }).reload()
    await flushPromises()

    expect(dashboard.kpi).toHaveBeenCalledWith({ noCache: true })
  })
})
