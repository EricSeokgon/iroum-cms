// SPEC-CMS-PUBLIC-001 T-009 — KpiChart 검증 (D-05)
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { axe } from 'jest-axe'

// vue-echarts / echarts 모듈은 jsdom 에서 canvas 의존 → mock
vi.mock('vue-echarts', () => ({
  default: {
    name: 'VChart',
    template: '<div class="mock-vchart" data-testid="mock-vchart"></div>',
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

import KpiChart from '@/components/stats/KpiChart.vue'
import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { WidgetData } from '@/api/statsApi'

function makeI18n() {
  return createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
}

describe('KpiChart — CARD 타입', () => {
  it('CARD 타입은 수치를 표시한다', () => {
    const widget: WidgetData = {
      code: 'visitors',
      title: '방문자 수',
      type: 'CARD',
      data: { value: 12345, label: '방문자', unit: '명' },
    }
    const wrapper = mount(KpiChart, {
      props: { widget },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('[data-testid="kpi-card-value"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('12,345')
    expect(wrapper.text()).toContain('명')
  })
})

describe('KpiChart — BAR 타입', () => {
  const widget: WidgetData = {
    code: 'monthly',
    title: '월별 통계',
    type: 'BAR',
    data: {
      categories: ['1월', '2월', '3월'],
      values: [10, 20, 30],
    },
  }

  it('BAR 차트가 vue-echarts mock 으로 렌더링된다', () => {
    const wrapper = mount(KpiChart, {
      props: { widget },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('[data-testid="kpi-chart-canvas"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="mock-vchart"]').exists()).toBe(true)
  })

  it('차트 데이터 보기 details 가 존재한다 (D-05)', () => {
    const wrapper = mount(KpiChart, {
      props: { widget },
      global: { plugins: [makeI18n()] },
    })
    const details = wrapper.find('details[data-testid="kpi-data-toggle"]')
    expect(details.exists()).toBe(true)
    expect(details.find('summary').text()).toContain('차트 데이터 보기')
  })

  it('데이터 테이블이 aria-label 과 함께 렌더링된다 (D-05)', () => {
    const wrapper = mount(KpiChart, {
      props: { widget },
      global: { plugins: [makeI18n()] },
    })
    const table = wrapper.find('table[data-testid="kpi-data-table"]')
    expect(table.exists()).toBe(true)
    expect(table.attributes('aria-label')).toContain('월별 통계')
    // 3 행 데이터
    const rows = table.findAll('tbody tr')
    expect(rows.length).toBe(3)
    expect(rows[0].text()).toContain('1월')
    expect(rows[0].text()).toContain('10')
  })
})

describe('KpiChart — PIE 타입', () => {
  it('PIE 데이터가 정상 처리된다', () => {
    const widget: WidgetData = {
      code: 'pie',
      title: '분포',
      type: 'PIE',
      data: { names: ['A', 'B'], values: [70, 30] },
    }
    const wrapper = mount(KpiChart, {
      props: { widget },
      global: { plugins: [makeI18n()] },
    })
    const table = wrapper.find('[data-testid="kpi-data-table"]')
    expect(table.exists()).toBe(true)
    const rows = table.findAll('tbody tr')
    expect(rows.length).toBe(2)
    expect(rows[0].text()).toContain('A')
    expect(rows[0].text()).toContain('70')
  })
})

describe('KpiChart — D-05 jest-axe 0 violations', () => {
  it('axe 위반 0건', async () => {
    const widget: WidgetData = {
      code: 'a11y',
      title: '월별 통계',
      type: 'BAR',
      data: { categories: ['1월', '2월'], values: [10, 20] },
    }
    const wrapper = mount(KpiChart, {
      props: { widget },
      global: { plugins: [makeI18n()] },
      attachTo: document.body,
    })
    const results = await axe(wrapper.element as HTMLElement)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })
})
