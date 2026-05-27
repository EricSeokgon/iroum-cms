// SPEC-CMS-PUBLIC-001 T-009 — PublicStatsView 검증 (D-05)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

// ECharts mocks
vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    template: '<div class="mock-vchart"></div>',
    props: ['option', 'autoresize'],
  },
}))
vi.mock('echarts/core', () => ({ use: vi.fn() }))
vi.mock('echarts/charts', () => ({ BarChart: {}, LineChart: {}, PieChart: {} }))
vi.mock('echarts/components', () => ({
  GridComponent: {},
  TooltipComponent: {},
  LegendComponent: {},
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const publicWidgetsMock = vi.fn()
vi.mock('@/api/statsApi', () => ({
  statsApi: {
    publicWidgets: (...args: unknown[]) => publicWidgetsMock(...args),
    widget: vi.fn(),
    kpiValues: vi.fn(),
  },
}))

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/stats', name: 'public-stats', component: () => import('@/views/PublicStatsView.vue') }],
  })
  router.push('/stats')
  await router.isReady()
  const PublicStatsView = (await import('@/views/PublicStatsView.vue')).default
  const wrapper = mount(PublicStatsView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('PublicStatsView — D-05', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    publicWidgetsMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 statsApi.publicWidgets() 호출', async () => {
    publicWidgetsMock.mockResolvedValue([{
      code: 'public-stats',
      title: '월별 통계',
      type: 'BAR',
      data: { categories: ['1월', '2월'], values: [10, 20] },
    }])
    await mountView()
    expect(publicWidgetsMock).toHaveBeenCalled()
  })

  it('단일 위젯 응답 → KpiChart 1 개 렌더링', async () => {
    publicWidgetsMock.mockResolvedValue([{
      code: 'monthly',
      title: '월별',
      type: 'BAR',
      data: { categories: ['1월', '2월'], values: [10, 20] },
    }])
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="kpi-chart-monthly"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="kpi-data-table"]').exists()).toBe(true)
  })

  it('다중 위젯 (배열) 응답도 처리한다', async () => {
    publicWidgetsMock.mockResolvedValue([
      { code: 'w1', title: '위젯1', type: 'CARD', data: { value: 100 } },
      { code: 'w2', title: '위젯2', type: 'CARD', data: { value: 200 } },
    ])
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="kpi-chart-w1"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="kpi-chart-w2"]').exists()).toBe(true)
  })

  it('API 실패 시 ErrorState 가 표시된다', async () => {
    publicWidgetsMock.mockRejectedValue(new Error('fail'))
    const wrapper = await mountView()
    expect(wrapper.find('[data-testid="error-state"]').exists()).toBe(true)
  })
})
