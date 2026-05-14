// SPEC-CMS-PUBLIC-001 T-007 — PolicyMatchView 검증 (C-03)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const matchMock = vi.fn()
vi.mock('@/api/policyApi', () => ({
  policyApi: {
    list: vi.fn(),
    detail: vi.fn(),
    match: (...args: unknown[]) => matchMock(...args),
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

describe('PolicyMatchView — C-03 익명 매칭 가능', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    matchMock.mockReset()
    localStorage.clear()
  })

  it('폼 제출 → policyApi.match 호출 + TOP-10 결과 렌더링', async () => {
    matchMock.mockResolvedValue([
      {
        policyId: 1,
        score: 0.92,
        reason: '업종 일치',
        policy: { id: 1, title: '정책1', industry: 'IT', region: '서울', type: '자금지원' },
      },
      {
        policyId: 2,
        score: 0.85,
        reason: '지역 일치',
        policy: { id: 2, title: '정책2', industry: 'IT', region: '서울', type: '컨설팅' },
      },
    ])
    const { wrapper } = await mountView()

    await wrapper.find('[data-testid="match-industry-input"]').setValue('IT')
    await wrapper.find('[data-testid="match-capital-input"]').setValue(50000000)
    await wrapper.find('[data-testid="match-revenue-input"]').setValue(100000000)
    await wrapper.find('[data-testid="match-employees-input"]').setValue(5)
    await wrapper.find('[data-testid="match-region-input"]').setValue('서울')
    await wrapper.find('form[data-testid="policy-match-form"]').trigger('submit')
    await flushPromises()

    expect(matchMock).toHaveBeenCalledWith({
      industry: 'IT',
      capitalAmount: 50000000,
      revenueAmount: 100000000,
      employeeCount: 5,
      region: '서울',
    })

    const results = wrapper.find('[data-testid="policy-match-results"]')
    expect(results.exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="policy-card"]').length).toBe(2)
  })

  it('401 응답 시에도 /login 으로 리다이렉트되지 않는다 (익명 가능)', async () => {
    // 익명 호출의 401 은 client 인터셉터에서 reject 만 발생. View 는 빈 결과로 처리.
    const axiosError = {
      isAxiosError: true,
      response: { status: 401, data: { code: 'UNAUTHORIZED' } },
    }
    matchMock.mockRejectedValue(axiosError)
    const { wrapper, router } = await mountView()

    await wrapper.find('[data-testid="match-industry-input"]').setValue('IT')
    await wrapper.find('form[data-testid="policy-match-form"]').trigger('submit')
    await flushPromises()

    // /login 으로 이동하지 않음 — 여전히 policy-match
    expect(router.currentRoute.value.name).toBe('policy-match')
  })

  it('결과가 빈 배열이면 matchEmpty 메시지', async () => {
    matchMock.mockResolvedValue([])
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
