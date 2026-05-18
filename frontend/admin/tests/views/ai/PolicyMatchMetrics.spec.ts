// AI 정책 추천 품질 지표 화면 — Vitest 단위 테스트 (SPEC-CMS-AI-002)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import PolicyMatchMetrics from '@/views/ai/PolicyMatchMetrics.vue'
import { policyMatchAdminApi } from '@/api/policyMatchAdminApi'
import type { PolicyMatchMetricsDto } from '@/api/policyMatchAdminApi'

vi.mock('@/api/policyMatchAdminApi', () => ({
  policyMatchAdminApi: {
    getMetrics: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function makeMetrics(
  overrides: Partial<PolicyMatchMetricsDto> = {},
): PolicyMatchMetricsDto {
  return {
    period: 'DAILY',
    ctr: 0.333,
    conversionRate: 0.1,
    coverage: 0.4,
    totalViewed: 3,
    totalClicked: 1,
    totalApplied: 0,
    ...overrides,
  }
}

function mountView() {
  return mount(PolicyMatchMetrics, {
    global: {
      plugins: [createTestingPinia({ createSpy: vi.fn }), i18n],
    },
  })
}

describe('PolicyMatchMetrics (SPEC-CMS-AI-002)', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트 시 metrics를 로드하고 CTR/전환율/커버리지 카드를 표시한다', async () => {
    vi.mocked(policyMatchAdminApi.getMetrics).mockResolvedValue({
      data: makeMetrics(),
    })

    const wrapper = mountView()
    await flushPromises()

    expect(policyMatchAdminApi.getMetrics).toHaveBeenCalledWith({
      period: 'DAILY',
    })
    expect(wrapper.find('[data-testid="metric-ctr"]').text()).toContain('33.3%')
    expect(wrapper.find('[data-testid="metric-conversion"]').text()).toContain(
      '10.0%',
    )
    expect(wrapper.find('[data-testid="metric-coverage"]').text()).toContain(
      '40.0%',
    )
  })

  it('pct() 헬퍼는 비율을 백분율 문자열로 변환한다', async () => {
    vi.mocked(policyMatchAdminApi.getMetrics).mockResolvedValue({
      data: makeMetrics(),
    })
    const wrapper = mountView()
    await flushPromises()
    const vm = wrapper.vm as unknown as {
      pct: (v: number | null) => string
    }
    expect(vm.pct(0.4)).toBe('40.0%')
    expect(vm.pct(null)).toBe('-')
  })

  it('기간 변경 후 검색하면 params.period가 반영된다', async () => {
    vi.mocked(policyMatchAdminApi.getMetrics).mockResolvedValue({
      data: makeMetrics({ period: 'WEEKLY' }),
    })
    const wrapper = mountView()
    await flushPromises()

    const vm = wrapper.vm as unknown as {
      period: { value: string } | string
      onSearch: () => void
    }
    // defineExpose ref unwrap — set then search
    ;(wrapper.vm as Record<string, unknown>).period = 'WEEKLY'
    vm.onSearch()
    await flushPromises()

    expect(policyMatchAdminApi.getMetrics).toHaveBeenLastCalledWith(
      expect.objectContaining({ period: 'WEEKLY' }),
    )
  })

  it('API 실패 시 metrics는 null이며 카드는 "-"를 표시한다', async () => {
    vi.mocked(policyMatchAdminApi.getMetrics).mockRejectedValue(
      new Error('500'),
    )
    const wrapper = mountView()
    await flushPromises()

    expect(wrapper.find('[data-testid="metric-ctr"]').text()).toContain('-')
  })
})
