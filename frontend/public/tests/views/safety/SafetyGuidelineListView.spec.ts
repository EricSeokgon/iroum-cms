// SPEC-CMS-PUBLIC-001 T-007 — SafetyGuidelineListView 검증
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const guidelinesMock = vi.fn()
vi.mock('@/api/safetyApi', () => ({
  safetyApi: {
    guidelines: (...args: unknown[]) => guidelinesMock(...args),
    guideline: vi.fn(),
    incidents: vi.fn(),
  },
}))

function makeGuideline(id: number, industryCode = 'IT') {
  return {
    id,
    title: `안전가이드-${id}`,
    industryCode,
    processCode: 'P01',
    updatedAt: '2026-04-01T00:00:00Z',
  }
}

function makePageResponse(items: ReturnType<typeof makeGuideline>[], total = items.length) {
  return {
    content: items,
    totalElements: total,
    totalPages: Math.ceil(total / 20),
    number: 0,
    size: 20,
  }
}

async function mountView(initialPath = '/safety/guidelines') {
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
        path: '/safety/guidelines',
        name: 'safety-guideline-list',
        component: () => import('@/views/safety/SafetyGuidelineListView.vue'),
      },
      {
        path: '/safety/guidelines/:id',
        name: 'safety-guideline-detail',
        component: { template: '<div />' },
      },
    ],
  })
  router.push(initialPath)
  await router.isReady()
  const SafetyGuidelineListView = (
    await import('@/views/safety/SafetyGuidelineListView.vue')
  ).default
  const wrapper = mount(SafetyGuidelineListView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('SafetyGuidelineListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    guidelinesMock.mockReset()
  })

  it('목록이 렌더링된다', async () => {
    guidelinesMock.mockResolvedValue(makePageResponse([makeGuideline(1), makeGuideline(2)]))
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="safety-guideline-list"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('안전가이드-1')
    expect(wrapper.text()).toContain('안전가이드-2')
  })

  it('빈 결과 시 EmptyState를 렌더링한다', async () => {
    guidelinesMock.mockResolvedValue(makePageResponse([]))
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="safety-guideline-list"]').exists()).toBe(false)
  })

  it('API 오류 시 ErrorState를 렌더링한다', async () => {
    guidelinesMock.mockRejectedValue(new Error('network'))
    const { wrapper } = await mountView()
    expect(wrapper.find('[data-testid="safety-guideline-list"]').exists()).toBe(false)
  })

  it('산업분류 필터를 제출하면 loadGuidelines가 재호출된다', async () => {
    guidelinesMock.mockResolvedValue(makePageResponse([makeGuideline(1)]))
    const { wrapper } = await mountView()
    expect(guidelinesMock).toHaveBeenCalledTimes(1)
    await wrapper.find('form[role="search"]').trigger('submit.prevent')
    await flushPromises()
    expect(guidelinesMock).toHaveBeenCalledTimes(2)
  })

  it('industryCode 쿼리 파라미터로 필터가 초기화된다', async () => {
    guidelinesMock.mockResolvedValue(makePageResponse([makeGuideline(3, '제조')]))
    await mountView('/safety/guidelines?industryCode=%EC%A0%9C%EC%A1%B0')
    expect(guidelinesMock).toHaveBeenCalledWith(
      expect.objectContaining({ industryCode: '제조' }),
    )
  })
})
