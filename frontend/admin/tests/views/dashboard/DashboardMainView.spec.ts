// DashboardMainView 단위 테스트 — SPEC-CMS-008 REQ-VIZ-002 ~ 005
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import DashboardMainView from '@/views/dashboard/DashboardMainView.vue'
import { dashboardApi } from '@/api/dashboard'
import type {
  WidgetResponse,
  WidgetDataResponse,
  SavedViewResponse,
  ExportResponse,
} from '@/api/dashboard'

// ECharts / vue-echarts 모킹
vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    template: '<div class="mock-chart" />',
    props: ['option', 'autoresize'],
  },
}))
vi.mock('echarts/core', () => ({ use: vi.fn() }))
vi.mock('echarts/charts', () => ({
  BarChart: {},
  LineChart: {},
  PieChart: {},
  RadarChart: {},
  HeatmapChart: {},
}))
vi.mock('echarts/components', () => ({
  GridComponent: {},
  TooltipComponent: {},
  LegendComponent: {},
  TitleComponent: {},
  VisualMapComponent: {},
  DatasetComponent: {},
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

vi.mock('@/api/dashboard', () => ({
  dashboardApi: {
    widgets: {
      list: vi.fn(),
      data: vi.fn(),
      preview: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    },
    views: {
      list: vi.fn(),
      create: vi.fn(),
      apply: vi.fn(),
      update: vi.fn(),
      delete: vi.fn(),
    },
    exports: {
      create: vi.fn(),
      status: vi.fn(),
      history: vi.fn(),
      download: vi.fn(),
    },
    layouts: { list: vi.fn() },
    cache: { invalidate: vi.fn(), stats: vi.fn() },
  },
}))

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: { ko: {} },
})

const MOCK_WIDGETS: WidgetResponse[] = [
  {
    id: 1,
    code: 'PV_BY_FEATURE',
    name: '기능별 PV',
    description: '최근 7일 페이지뷰',
    widget_type: 'BAR_CHART',
    data_source: 'KPI_VALUE',
    status: 'ACTIVE',
  },
  {
    id: 2,
    code: 'SIGNUP_TREND',
    name: '가입자 추이',
    widget_type: 'LINE_CHART',
    data_source: 'KPI_VALUE',
    status: 'ACTIVE',
  },
  {
    id: 3,
    code: 'INACTIVE_WIDGET',
    name: '비활성 위젯',
    widget_type: 'METRIC_CARD',
    data_source: 'KPI_VALUE',
    status: 'INACTIVE',
  },
]

const MOCK_DATA: WidgetDataResponse = {
  widget: { id: 1, code: 'PV_BY_FEATURE', type: 'BAR_CHART' },
  available_dimensions: ['feature'],
  applied_filter: {},
  dataset: {
    categories: ['board', 'policy', 'safety'],
    series: [{ name: 'PV', data: [120, 80, 60] }],
  },
  generated_at: '2026-05-13T00:00:00Z',
  cache_hit: false,
}

const MOCK_VIEWS: SavedViewResponse[] = [
  {
    id: 10,
    owner_id: 1,
    name: '기능별 7일',
    filter_state: JSON.stringify({ period: '7d', features: ['board'] }),
    is_default: false,
    is_shared: false,
  },
]

function buildWrapper() {
  return mount(DashboardMainView, {
    global: {
      plugins: [
        i18n,
        ElementPlus,
        createTestingPinia({ stubActions: false }),
      ],
      stubs: {
        'el-date-picker': true,
      },
    },
  })
}

describe('DashboardMainView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dashboardApi.widgets.list).mockResolvedValue({
      data: MOCK_WIDGETS,
    } as never)
    vi.mocked(dashboardApi.widgets.data).mockResolvedValue({
      data: MOCK_DATA,
    } as never)
    vi.mocked(dashboardApi.views.list).mockResolvedValue({
      data: MOCK_VIEWS,
    } as never)
    vi.mocked(dashboardApi.exports.create).mockResolvedValue({
      data: {
        id: 999,
        requestor_id: 1,
        export_type: 'EXCEL',
        scope: '{}',
        status: 'PENDING',
      } as ExportResponse,
    } as never)
  })

  it('마운트 시 위젯 목록과 저장된 뷰를 로드한다', async () => {
    buildWrapper()
    await flushPromises()
    expect(dashboardApi.widgets.list).toHaveBeenCalled()
    expect(dashboardApi.views.list).toHaveBeenCalled()
  })

  it('ACTIVE 상태 위젯만 그리드에 렌더링된다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    const html = wrapper.html()
    expect(html).toContain('기능별 PV')
    expect(html).toContain('가입자 추이')
    // INACTIVE 위젯은 화면에 표시되지 않음
    expect(html).not.toContain('비활성 위젯')
  })

  it('위젯 그리드가 비어있을 때 empty 메시지를 표시한다', async () => {
    vi.mocked(dashboardApi.widgets.list).mockResolvedValue({ data: [] } as never)
    const wrapper = buildWrapper()
    await flushPromises()
    expect(wrapper.html()).toContain('등록된 위젯이 없습니다')
  })

  it('새로고침 버튼 클릭 시 위젯 목록이 재호출된다', async () => {
    const wrapper = buildWrapper()
    await flushPromises()
    vi.mocked(dashboardApi.widgets.list).mockClear()

    // 새로고침 버튼 찾아 클릭
    const refreshBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('새로고침'))
    expect(refreshBtn).toBeDefined()
    await refreshBtn!.trigger('click')
    await flushPromises()

    expect(dashboardApi.widgets.list).toHaveBeenCalled()
  })

  it('각 ACTIVE 위젯에 대해 widget data API가 호출된다', async () => {
    buildWrapper()
    await flushPromises()
    // ACTIVE 위젯 2개에 대해 data() 호출
    expect(dashboardApi.widgets.data).toHaveBeenCalledTimes(2)
    expect(dashboardApi.widgets.data).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ from: expect.any(String), to: expect.any(String) }),
    )
  })
})
