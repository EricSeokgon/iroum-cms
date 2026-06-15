// SPEC-CMS-KPI-002 — 운영 활동 KPI 위젯 렌더 테스트 (REQ-KPI2-007-1/3)
import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import KpiActivityCards from '@/components/dashboard/KpiActivityCards.vue'
import KpiContentViewChart from '@/components/dashboard/KpiContentViewChart.vue'
import { KPI_CODES, type KpiValueItem } from '@/api/kpi'

// vue-echarts 는 ResizeObserver 등 브라우저 API 에 의존 → 스텁 처리
vi.mock('vue-echarts', () => ({
  default: { name: 'VChart', template: '<div class="v-chart-stub" />' },
}))

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      kpi: {
        card: { vsPrevious: '이전 기간 대비' },
        code: {
          DAU: '일 활성 사용자',
          MAU: '월 활성 사용자',
          API_ERROR_RATE: 'API 오류율',
          CONTENT_VIEW: '콘텐츠 조회 수',
        },
        chart: { contentViewTitle: '콘텐츠 유형별 조회 수', count: '건수', empty: '없음' },
        contentType: { notice: '공지', post: '게시물', publication: '발간자료' },
      },
    },
  },
})

function item(kpiCode: string, value: number, dimension: Record<string, string>): KpiValueItem {
  return {
    kpiCode,
    kpiName: kpiCode,
    dimensionJson: JSON.stringify(dimension),
    value,
    aggregatedAt: '2026-06-12T00:00:00Z',
    dataState: 'READY',
  }
}

describe('KpiActivityCards (SPEC-CMS-KPI-002)', () => {
  it('DAU/MAU/오류율 3개 카드를 렌더링한다', () => {
    const wrapper = mount(KpiActivityCards, {
      global: { plugins: [i18n] },
      props: {
        dauItems: [item(KPI_CODES.DAU, 120, { date: '2026-06-12' })],
        mauItems: [item(KPI_CODES.MAU, 3400, { month: '2026-06' })],
        errorRateItems: [item(KPI_CODES.API_ERROR_RATE, 1.25, { date: '2026-06-12' })],
      },
    })
    const regions = wrapper.findAll('[role="region"]')
    expect(regions).toHaveLength(3)
    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).toContain('1.25%')
  })

  it('데이터 없으면 — 로 표시한다', () => {
    const wrapper = mount(KpiActivityCards, {
      global: { plugins: [i18n] },
      props: { dauItems: [], mauItems: [], errorRateItems: [] },
    })
    expect(wrapper.text()).toContain('—')
  })
})

describe('KpiContentViewChart (SPEC-CMS-KPI-002)', () => {
  it('콘텐츠 유형 데이터가 있으면 차트를 렌더링한다', () => {
    const wrapper = mount(KpiContentViewChart, {
      global: { plugins: [i18n] },
      props: {
        items: [
          item(KPI_CODES.CONTENT_VIEW, 50, { date: '2026-06-12', contentType: 'notice' }),
          item(KPI_CODES.CONTENT_VIEW, 30, { date: '2026-06-12', contentType: 'post' }),
        ],
      },
    })
    expect(wrapper.find('.v-chart-stub').exists()).toBe(true)
  })

  it('데이터 없으면 empty 메시지를 표시한다', () => {
    const wrapper = mount(KpiContentViewChart, {
      global: { plugins: [i18n] },
      props: { items: [] },
    })
    expect(wrapper.find('.v-chart-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('없음')
  })
})
