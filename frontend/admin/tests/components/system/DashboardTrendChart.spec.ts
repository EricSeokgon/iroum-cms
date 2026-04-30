// DashboardTrendChart 컴포넌트 단위 테스트 — SPEC-CMS-005 Bundle D
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import ko from '@/locales/ko.json'
import DashboardTrendChart from '@/components/system/DashboardTrendChart.vue'
import type { TrendItemResponse } from '@/api/system'

// vue-echarts 모킹 — jsdom 환경에서 canvas 불필요
vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    template: '<div class="mock-chart" />',
    props: ['option', 'autoresize'],
  },
}))

vi.mock('echarts/core', () => ({
  use: vi.fn(),
}))
vi.mock('echarts/charts', () => ({ LineChart: {} }))
vi.mock('echarts/components', () => ({
  GridComponent: {},
  TooltipComponent: {},
  LegendComponent: {},
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

const sampleItems: TrendItemResponse[] = [
  { date: '2026-04-01', visits: 100, page_views: 300, errors: 5 },
  { date: '2026-04-02', visits: 120, page_views: 360, errors: 3 },
]

describe('DashboardTrendChart', () => {
  it('컴포넌트가 마운트된다', () => {
    const wrapper = mount(DashboardTrendChart, {
      props: { items: sampleItems, days: 30 },
      global: { plugins: [i18n, ElementPlus] },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('기간 버튼이 7, 30, 90으로 3개 렌더링된다', () => {
    const wrapper = mount(DashboardTrendChart, {
      props: { items: sampleItems, days: 30 },
      global: { plugins: [i18n, ElementPlus] },
    })
    const buttons = wrapper.findAll('button')
    expect(buttons.length).toBeGreaterThanOrEqual(3)
  })

  it('chartOption의 xAxis.data에 날짜 배열이 포함된다', () => {
    const wrapper = mount(DashboardTrendChart, {
      props: { items: sampleItems, days: 30 },
      global: { plugins: [i18n, ElementPlus] },
    })
    const vm = wrapper.vm as { chartOption: { xAxis: { data: string[] } } }
    expect(vm.chartOption.xAxis.data).toEqual(['2026-04-01', '2026-04-02'])
  })

  it('chartOption.series에 visits/page_views/errors 데이터가 포함된다', () => {
    const wrapper = mount(DashboardTrendChart, {
      props: { items: sampleItems, days: 30 },
      global: { plugins: [i18n, ElementPlus] },
    })
    const vm = wrapper.vm as { chartOption: { series: Array<{ data: number[] }> } }
    const seriesData = vm.chartOption.series.map(s => s.data)
    expect(seriesData[0]).toEqual([100, 120])
    expect(seriesData[1]).toEqual([300, 360])
    expect(seriesData[2]).toEqual([5, 3])
  })

  it('30 버튼 클릭 시 update:days 이벤트 발생', async () => {
    const wrapper = mount(DashboardTrendChart, {
      props: { items: sampleItems, days: 7 },
      global: { plugins: [i18n, ElementPlus] },
    })
    const buttons = wrapper.findAll('button')
    // 30일 버튼 (index 1: [7, 30, 90] 순서)
    await buttons[1].trigger('click')
    expect(wrapper.emitted('update:days')).toBeTruthy()
    expect(wrapper.emitted('update:days')![0]).toEqual([30])
  })
})
