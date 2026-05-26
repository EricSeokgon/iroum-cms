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
import { usersApi } from '@/api/users'
import { mediaApi } from '@/api/media'
import { listQnas } from '@/api/qna'
import { listSurveys } from '@/api/survey'
import { listFaqs } from '@/api/faq'
import { boardApi } from '@/api/board'

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

vi.mock('@/api/users', () => ({
  usersApi: { list: vi.fn() },
}))
vi.mock('@/api/media', () => ({
  mediaApi: { list: vi.fn() },
}))
vi.mock('@/api/qna', () => ({
  listQnas: vi.fn(),
}))
vi.mock('@/api/survey', () => ({
  listSurveys: vi.fn(),
}))
vi.mock('@/api/faq', () => ({
  listFaqs: vi.fn(),
}))
vi.mock('@/api/board', () => ({
  boardApi: { listMasters: vi.fn() },
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
    vi.mocked(usersApi.list).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(mediaApi.list).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(boardApi.listMasters).mockResolvedValue({ data: [] } as never)
    vi.mocked(listQnas).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(listSurveys).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(listFaqs).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
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

  it('KPI 카드가 여러 개 렌더링된다', async () => {
    const wrapper = mount(SystemDashboardView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia({ stubActions: false })] },
    })
    await flushPromises()
    // KpiCard는 role="region"
    const cards = wrapper.findAll('[role="region"]')
    expect(cards.length).toBeGreaterThanOrEqual(10)
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

  it('강제 새로고침 체크박스 체크 후 버튼 클릭 시 noCache: true로 kpi 호출', async () => {
    const wrapper = mount(SystemDashboardView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia({ stubActions: false })] },
    })
    await flushPromises()
    vi.mocked(dashboard.kpi).mockClear()
    vi.mocked(usersApi.list).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(mediaApi.list).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(boardApi.listMasters).mockResolvedValue({ data: [] } as never)
    vi.mocked(listQnas).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(listSurveys).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)
    vi.mocked(listFaqs).mockResolvedValue({ data: { content: [], totalElements: 0 } } as never)

    // el-checkbox에 update:modelValue 이벤트를 직접 emit해 noCache = true 설정
    const elCheckbox = wrapper.findComponent({ name: 'ElCheckbox' })
    await elCheckbox.vm.$emit('update:modelValue', true)
    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(dashboard.kpi).toHaveBeenCalledWith({ noCache: true })
  })
})
