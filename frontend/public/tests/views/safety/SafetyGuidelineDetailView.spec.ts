// SPEC-CMS-PUBLIC-001 T-007 — SafetyGuidelineDetailView 검증 (C-05)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { SafetyGuidelineDetail } from '@/api/safetyApi'

const guidelineMock = vi.fn()
vi.mock('@/api/safetyApi', () => ({
  safetyApi: {
    guidelines: vi.fn(),
    guideline: (...args: unknown[]) => guidelineMock(...args),
    incidents: vi.fn(),
  },
}))

function makeGuideline(): SafetyGuidelineDetail {
  return {
    id: 1,
    title: '건설현장 안전수칙',
    industryCode: '건설',
    processCode: '시공',
    updatedAt: '2026-04-01T00:00:00Z',
    descriptionHtml: '<p>안전 가이드 본문</p>',
    checklist: [
      { id: 11, text: '안전모를 착용한다', order: 1 },
      { id: 12, text: '안전 표지판을 확인한다', order: 2 },
      { id: 13, text: '비상 연락처 확인', order: 3 },
    ],
    relatedIncidentIds: [],
  }
}

async function mountView(routeId = '1') {
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
        path: '/safety/guidelines/:id',
        name: 'safety-guideline-detail',
        component: () => import('@/views/safety/SafetyGuidelineDetailView.vue'),
      },
    ],
  })
  router.push(`/safety/guidelines/${routeId}`)
  await router.isReady()
  const SafetyGuidelineDetailView = (
    await import('@/views/safety/SafetyGuidelineDetailView.vue')
  ).default
  const wrapper = mount(SafetyGuidelineDetailView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('SafetyGuidelineDetailView — C-05 체크리스트 + 인쇄', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    guidelineMock.mockReset()
    localStorage.clear()
  })

  it('체크리스트가 SafetyChecklist 컴포넌트로 렌더링된다', async () => {
    guidelineMock.mockResolvedValue(makeGuideline())
    const { wrapper } = await mountView()
    const list = wrapper.find('[data-testid="safety-checklist"]')
    expect(list.exists()).toBe(true)
    const items = wrapper.findAll('[data-testid="safety-checklist-item"]')
    expect(items.length).toBe(3)
  })

  it('인쇄 버튼 클릭 → window.print() 호출', async () => {
    guidelineMock.mockResolvedValue(makeGuideline())
    const printSpy = vi.spyOn(window, 'print').mockImplementation(() => undefined)
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="safety-print-button"]').trigger('click')
    expect(printSpy).toHaveBeenCalledTimes(1)
    printSpy.mockRestore()
  })
})
