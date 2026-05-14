// SPEC-CMS-PUBLIC-001 T-007 — PublicationListView 검증 (C-07)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const getMock = vi.fn()
vi.mock('@/api/client', () => ({
  apiClient: {
    get: (...args: unknown[]) => getMock(...args),
    post: vi.fn(),
  },
  ACCESS_TOKEN_KEY: 'public.accessToken',
  REFRESH_TOKEN_KEY: 'public.refreshToken',
}))

function makePublication(id: number) {
  return {
    id,
    title: `발간자료-${id}`,
    publicationYear: 2025,
    documentType: 'RESEARCH',
    categoryId: 12,
    thumbnailUrl: `https://cdn.example.com/thumb-${id}.png`,
    downloadCount: 42 + id,
  }
}

async function mountView(initialPath = '/publications') {
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
        path: '/publications',
        name: 'publication-list',
        component: () => import('@/views/publications/PublicationListView.vue'),
      },
      { path: '/publications/:id', name: 'publication-detail', component: { template: '<div />' } },
    ],
  })
  router.push(initialPath)
  await router.isReady()
  const PublicationListView = (
    await import('@/views/publications/PublicationListView.vue')
  ).default
  const wrapper = mount(PublicationListView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('PublicationListView — C-07 다중 필터', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    getMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 apiClient.get("/publications", {params: {page:0, size:20}}) 호출', async () => {
    getMock.mockResolvedValue({
      data: {
        content: [makePublication(1)],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      },
    })
    await mountView()
    expect(getMock).toHaveBeenCalledTimes(1)
    expect(getMock.mock.calls[0][0]).toBe('/publications')
    const params = getMock.mock.calls[0][1].params
    expect(params).toMatchObject({ page: 0, size: 20 })
  })

  it('year/documentType/categoryId 필터 적용 → params 에 포함', async () => {
    getMock.mockResolvedValue({
      data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="publication-year-select"]').setValue('2025')
    await wrapper.find('[data-testid="publication-doctype-select"]').setValue('RESEARCH')
    await wrapper.find('[data-testid="publication-category-input"]').setValue('12')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const lastCall = getMock.mock.calls[getMock.mock.calls.length - 1]
    expect(lastCall[1].params).toMatchObject({
      page: 0,
      size: 20,
      year: 2025,
      documentType: 'RESEARCH',
      categoryId: 12,
    })
  })

  it('PublicationCard 가 thumbnailUrl, title, publicationYear, downloadCount 를 표시', async () => {
    getMock.mockResolvedValue({
      data: {
        content: [makePublication(1), makePublication(2)],
        page: 0,
        size: 20,
        totalElements: 2,
        totalPages: 1,
      },
    })
    const { wrapper } = await mountView()
    const cards = wrapper.findAll('[data-testid="publication-card"]')
    expect(cards.length).toBe(2)
    const first = cards[0]
    expect(first.find('[data-testid="publication-thumbnail"]').attributes('src')).toBe(
      'https://cdn.example.com/thumb-1.png',
    )
    expect(first.find('[data-testid="publication-year"]').text()).toBe('2025')
    expect(first.find('[data-testid="publication-download-count"]').text()).toContain('43')
    expect(first.text()).toContain('발간자료-1')
  })
})
