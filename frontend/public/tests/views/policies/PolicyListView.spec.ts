// SPEC-CMS-PUBLIC-001 T-007 — PolicyListView 검증 (C-01)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const listMock = vi.fn()
vi.mock('@/api/policyApi', () => ({
  policyApi: {
    list: (...args: unknown[]) => listMock(...args),
    detail: vi.fn(),
    match: vi.fn(),
  },
}))

function makePolicy(id: number) {
  return {
    id,
    title: `정책-${id}`,
    industry: 'IT',
    region: '서울',
    type: '자금지원',
  }
}

async function mountView(initialPath = '/policies') {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/policies',
        name: 'policy-list',
        component: () => import('@/views/policies/PolicyListView.vue'),
      },
      { path: '/policies/:id', name: 'policy-detail', component: { template: '<div />' } },
    ],
  })
  router.push(initialPath)
  await router.isReady()
  const PolicyListView = (await import('@/views/policies/PolicyListView.vue')).default
  const wrapper = mount(PolicyListView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('PolicyListView — C-01 다중 필터', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    listMock.mockResolvedValue({
      content: [makePolicy(1), makePolicy(2)],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
    localStorage.clear()
  })

  it('마운트 시 policyApi.list({page:0, size:20}) 호출', async () => {
    await mountView()
    expect(listMock).toHaveBeenCalledTimes(1)
    const firstCall = listMock.mock.calls[0][0]
    expect(firstCall).toMatchObject({ page: 0, size: 20 })
  })

  it('industry/region/type 필터 적용 → policyApi.list 가 매개변수와 함께 호출', async () => {
    const { wrapper } = await mountView()

    await wrapper.find('[data-testid="policy-industry-select"]').setValue('IT')
    await wrapper.find('[data-testid="policy-region-select"]').setValue('서울')
    await wrapper.find('[data-testid="policy-type-자금지원"]').setValue(true)
    await wrapper.find('form[data-testid="policy-filter-bar"]').trigger('submit')
    await flushPromises()

    const lastCall = listMock.mock.calls[listMock.mock.calls.length - 1][0]
    expect(lastCall).toMatchObject({
      page: 0,
      size: 20,
      industry: 'IT',
      region: '서울',
      type: '자금지원',
    })
  })

  it('필터 초기화 버튼 → 모든 필터 클리어 + URL 이 /policies 로 리셋', async () => {
    const { wrapper, router } = await mountView('/policies?industry=IT&region=서울&type=자금지원')
    await flushPromises()
    await wrapper.find('[data-testid="policy-filter-reset"]').trigger('click')
    await flushPromises()
    // URL 이 /policies (query 없음) 로 리셋되어야 함
    expect(Object.keys(router.currentRoute.value.query).length).toBe(0)
    expect(router.currentRoute.value.path).toBe('/policies')
  })

  it('카드 목록 렌더링 + PaginationBar 영역', async () => {
    listMock.mockResolvedValue({
      content: Array.from({ length: 20 }, (_, i) => makePolicy(i + 1)),
      page: 0,
      size: 20,
      totalElements: 40,
      totalPages: 2,
    })
    const { wrapper } = await mountView()
    const cards = wrapper.findAll('[data-testid="policy-card"]')
    expect(cards.length).toBe(20)
  })
})
