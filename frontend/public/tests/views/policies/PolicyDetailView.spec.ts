// SPEC-CMS-PUBLIC-001 T-007 — PolicyDetailView 검증 (C-02, C-04)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { PolicyDetail } from '@/api/policyApi'

const detailMock = vi.fn()
vi.mock('@/api/policyApi', () => ({
  policyApi: {
    list: vi.fn(),
    detail: (...args: unknown[]) => detailMock(...args),
    match: vi.fn(),
  },
}))

const postMock = vi.fn()
vi.mock('@/api/client', () => ({
  apiClient: {
    post: (...args: unknown[]) => postMock(...args),
  },
  ACCESS_TOKEN_KEY: 'public.accessToken',
  REFRESH_TOKEN_KEY: 'public.refreshToken',
}))

const elMessageMock = { error: vi.fn(), success: vi.fn(), info: vi.fn(), warning: vi.fn() }
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<Record<string, unknown>>('element-plus')
  return { ...actual, ElMessage: elMessageMock }
})

function makeDetail(over: Partial<PolicyDetail> = {}): PolicyDetail {
  return {
    id: 123,
    title: '스타트업 자금 지원',
    industry: 'IT',
    region: '서울',
    type: '자금지원',
    supportAmount: '최대 1억',
    deadline: '2026-08-31T00:00:00Z',
    descriptionHtml: '<p>정책 본문</p>',
    eligibilityHtml: '<p>자격 요건</p>',
    applyUrl: 'https://gov.kr/apply/123',
    contact: '02-000-0000',
    ...over,
  }
}

async function mountView(routeId = '123') {
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
      { path: '/policies', name: 'policy-list', component: { template: '<div />' } },
      {
        path: '/policies/:id',
        name: 'policy-detail',
        component: () => import('@/views/policies/PolicyDetailView.vue'),
      },
    ],
  })
  router.push(`/policies/${routeId}`)
  await router.isReady()
  const PolicyDetailView = (await import('@/views/policies/PolicyDetailView.vue')).default
  const wrapper = mount(PolicyDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('PolicyDetailView — C-02 외부 신청 링크 안전', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    postMock.mockReset()
    Object.values(elMessageMock).forEach((m) => m.mockReset())
    localStorage.clear()
  })

  it('https:// 링크 → target=_blank + rel="noopener noreferrer" 부착', async () => {
    detailMock.mockResolvedValue(makeDetail())
    const { wrapper } = await mountView()
    const link = wrapper.find('[data-testid="apply-external-link"]')
    expect(link.exists()).toBe(true)
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toBe('noopener noreferrer')
    expect(link.attributes('href')).toBe('https://gov.kr/apply/123')
  })

  it('도메인이 별도 표시(aria-label 또는 가시 텍스트)', async () => {
    detailMock.mockResolvedValue(makeDetail())
    const { wrapper } = await mountView()
    const link = wrapper.find('[data-testid="apply-external-link"]')
    // aria-label 에 도메인 포함
    expect(link.attributes('aria-label')).toContain('gov.kr')
    // 가시 도메인 텍스트 노출
    const domain = wrapper.find('[data-testid="apply-domain"]')
    expect(domain.exists()).toBe(true)
    expect(domain.text()).toBe('gov.kr')
  })

  it('javascript: 스킴은 차단되어 disabled 버튼 + 안내 메시지', async () => {
    detailMock.mockResolvedValue(makeDetail({ applyUrl: 'javascript:alert(1)' }))
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="apply-external-link"]').exists()).toBe(false)
    const disabled = wrapper.find('[data-testid="apply-external-disabled"]')
    expect(disabled.exists()).toBe(true)
    expect(disabled.attributes('disabled')).toBeDefined()
    expect(wrapper.find('[data-testid="apply-link-unsafe"]').text()).toContain('신청 링크 확인 중입니다')
  })

  it('data: 스킴은 차단', async () => {
    detailMock.mockResolvedValue(makeDetail({ applyUrl: 'data:text/html,<script>alert(1)</script>' }))
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="apply-external-link"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="apply-external-disabled"]').exists()).toBe(true)
  })

  it('file: 스킴은 차단', async () => {
    detailMock.mockResolvedValue(makeDetail({ applyUrl: 'file:///etc/passwd' }))
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="apply-external-link"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="apply-external-disabled"]').exists()).toBe(true)
  })
})

describe('PolicyDetailView — C-04 알림 구독 인증 필요', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    postMock.mockReset()
    Object.values(elMessageMock).forEach((m) => m.mockReset())
    localStorage.clear()
  })

  it('비인증 사용자가 구독 버튼 클릭 → /login?redirect=/policies/subscriptions?policyId=... 로 이동', async () => {
    detailMock.mockResolvedValue(makeDetail({ id: 123 }))
    const { wrapper, router } = await mountView('123')
    await wrapper.find('[data-testid="policy-subscribe-button"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe(
      '/policies/subscriptions?policyId=123',
    )
    expect(postMock).not.toHaveBeenCalled()
  })

  it('인증된 사용자가 구독 버튼 클릭 → POST /policies/subscriptions {policyId}', async () => {
    detailMock.mockResolvedValue(makeDetail({ id: 123 }))
    postMock.mockResolvedValue({ data: { id: 99 } })
    localStorage.setItem('public.accessToken', 'fake-token')
    const { wrapper, router } = await mountView('123')
    await wrapper.find('[data-testid="policy-subscribe-button"]').trigger('click')
    await flushPromises()
    expect(postMock).toHaveBeenCalledWith('/policies/subscriptions', { policyId: 123 })
    // login 으로 이동하지 않아야 함
    expect(router.currentRoute.value.name).toBe('policy-detail')
  })
})
