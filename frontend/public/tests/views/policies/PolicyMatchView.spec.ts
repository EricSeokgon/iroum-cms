// SPEC-CMS-PUBLIC-001 T-007 — PolicyMatchView 검증 (C-03)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

// SPEC-CMS-AI-002 — PolicyMatchView가 AI 하이브리드 추천(aiMatch) + 피드백(sendFeedback)을
// 사용하도록 강화됨. C-03 익명 매칭 invariant(401 무리다이렉트·route meta)는 그대로 유지.
const aiMatchMock = vi.fn()
const sendFeedbackMock = vi.fn()
vi.mock('@/api/policyApi', () => ({
  policyApi: {
    list: vi.fn(),
    detail: vi.fn(),
    match: vi.fn(),
    aiMatch: (...args: unknown[]) => aiMatchMock(...args),
    sendFeedback: (...args: unknown[]) => sendFeedbackMock(...args),
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
    routes: [
      { path: '/login', name: 'login', component: { template: '<div />' } },
      { path: '/policies/:id', name: 'policy-detail', component: { template: '<div />' } },
      {
        path: '/policies/match',
        name: 'policy-match',
        component: () => import('@/views/policies/PolicyMatchView.vue'),
      },
    ],
  })
  router.push('/policies/match')
  await router.isReady()
  const PolicyMatchView = (await import('@/views/policies/PolicyMatchView.vue')).default
  const wrapper = mount(PolicyMatchView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('PolicyMatchView — C-03 익명 매칭 + SPEC-CMS-AI-002 하이브리드', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    aiMatchMock.mockReset()
    sendFeedbackMock.mockReset()
    sendFeedbackMock.mockResolvedValue(undefined)
    localStorage.clear()
  })

  it('폼 제출 → policyApi.aiMatch 호출(화이트리스트 프로필) + 하이브리드 점수 배지 렌더링', async () => {
    aiMatchMock.mockResolvedValue({
      degraded: false,
      items: [
        {
          policyId: 1,
          hybridScore: 0.92,
          ruleScore: 0.8,
          semanticScore: 0.98,
          explanation: {
            ruleBreakdown: { industry: 30 },
            matchedTerms: ['IT'],
            rationale: '업종 시맨틱 매칭',
            semanticAvailable: true,
          },
        },
        {
          policyId: 2,
          hybridScore: 0.85,
          ruleScore: 0.7,
          semanticScore: 0.95,
          explanation: {
            ruleBreakdown: { region: 20 },
            matchedTerms: ['서울'],
            rationale: '지역 시맨틱 매칭',
            semanticAvailable: true,
          },
        },
      ],
    })
    const { wrapper } = await mountView()

    await wrapper.find('[data-testid="match-industry-input"]').setValue('IT')
    await wrapper.find('[data-testid="match-revenue-input"]').setValue(100000000)
    await wrapper.find('[data-testid="match-employees-input"]').setValue(5)
    await wrapper.find('[data-testid="match-region-input"]').setValue('서울')
    await wrapper.find('form[data-testid="policy-match-form"]').trigger('submit')
    await flushPromises()

    // AC-PM: companyProfile은 화이트리스트 키만 (PII 제외)
    expect(aiMatchMock).toHaveBeenCalledWith({
      companyProfile: {
        ksic_code: 'IT',
        employee_count: 5,
        region_code: '서울',
        annual_revenue: 100000000,
      },
      topK: 10,
    })

    expect(wrapper.find('[data-testid="policy-match-results"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="hybrid-badge-1"]').text()).toContain('92.0%')
    expect(wrapper.find('[data-testid="hybrid-badge-2"]').text()).toContain('85.0%')
  })

  it('AC-PM-013: 정책 클릭 시 CLICKED, 신청 시 APPLIED 피드백 전송', async () => {
    aiMatchMock.mockResolvedValue({
      degraded: false,
      items: [
        {
          policyId: 7,
          hybridScore: 0.7,
          ruleScore: 0.5,
          semanticScore: 0.8,
          explanation: {
            ruleBreakdown: {},
            matchedTerms: [],
            rationale: 'r',
            semanticAvailable: true,
          },
        },
      ],
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="match-industry-input"]').setValue('IT')
    await wrapper.find('form[data-testid="policy-match-form"]').trigger('submit')
    await flushPromises()

    await wrapper.find('[data-testid="policy-link-7"]').trigger('click')
    await flushPromises()
    expect(sendFeedbackMock).toHaveBeenCalledWith(
      expect.objectContaining({ interactionType: 'CLICKED', policyId: 7 }),
    )

    await wrapper.find('[data-testid="apply-btn-7"]').trigger('click')
    await flushPromises()
    expect(sendFeedbackMock).toHaveBeenCalledWith(
      expect.objectContaining({ interactionType: 'APPLIED', policyId: 7 }),
    )
  })

  it('AC-PM-009: degraded=true면 폴백 배너를 표시한다', async () => {
    aiMatchMock.mockResolvedValue({
      degraded: true,
      items: [
        {
          policyId: 3,
          hybridScore: 0.6,
          ruleScore: 0.6,
          semanticScore: 0,
          explanation: {
            ruleBreakdown: { industry: 30 },
            matchedTerms: [],
            rationale: '규칙 기반',
            semanticAvailable: false,
          },
        },
      ],
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="match-industry-input"]').setValue('IT')
    await wrapper.find('form[data-testid="policy-match-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.find('[data-testid="ai-degraded-banner"]').exists()).toBe(true)
  })

  it('401 응답 시에도 /login 으로 리다이렉트되지 않는다 (익명 가능, C-03 유지)', async () => {
    const axiosError = {
      isAxiosError: true,
      response: { status: 401, data: { code: 'UNAUTHORIZED' } },
    }
    aiMatchMock.mockRejectedValue(axiosError)
    const { wrapper, router } = await mountView()

    await wrapper.find('[data-testid="match-industry-input"]').setValue('IT')
    await wrapper.find('form[data-testid="policy-match-form"]').trigger('submit')
    await flushPromises()

    expect(router.currentRoute.value.name).toBe('policy-match')
  })

  it('결과가 빈 배열이면 matchEmpty 메시지', async () => {
    aiMatchMock.mockResolvedValue({ degraded: false, items: [] })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="match-industry-input"]').setValue('농업')
    await wrapper.find('form[data-testid="policy-match-form"]').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('조건에 맞는 정책이 없습니다')
  })

  it('policy-match 라우트는 requiresAuth 메타가 false 또는 미설정', async () => {
    const router = (await import('@/router')).default
    const route = router.getRoutes().find((r) => r.name === 'policy-match')
    expect(route).toBeDefined()
    expect(route?.meta.requiresAuth).not.toBe(true)
  })
})
