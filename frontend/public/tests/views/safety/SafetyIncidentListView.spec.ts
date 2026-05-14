// SPEC-CMS-PUBLIC-001 T-007 — SafetyIncidentListView 검증 (C-06)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const incidentsMock = vi.fn()
vi.mock('@/api/safetyApi', () => ({
  safetyApi: {
    guidelines: vi.fn(),
    guideline: vi.fn(),
    incidents: (...args: unknown[]) => incidentsMock(...args),
  },
}))

function makeIncident(id: number, industry = 'IT') {
  return {
    id,
    title: `사고-${id}`,
    industryCode: industry,
    occurredAt: '2026-03-15T00:00:00Z',
    summary: `사고 요약 ${id}`,
  }
}

async function mountView(initialPath = '/safety/incidents') {
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
        path: '/safety/incidents',
        name: 'safety-incident-list',
        component: () => import('@/views/safety/SafetyIncidentListView.vue'),
      },
    ],
  })
  router.push(initialPath)
  await router.isReady()
  const SafetyIncidentListView = (
    await import('@/views/safety/SafetyIncidentListView.vue')
  ).default
  const wrapper = mount(SafetyIncidentListView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('SafetyIncidentListView — C-06 공개 필터', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    incidentsMock.mockReset()
    localStorage.clear()
  })

  it('마운트 시 safetyApi.incidents({page:0, size:20}) 호출', async () => {
    incidentsMock.mockResolvedValue({
      content: [makeIncident(1), makeIncident(2)],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
    await mountView()
    expect(incidentsMock).toHaveBeenCalledTimes(1)
    const firstCall = incidentsMock.mock.calls[0][0]
    expect(firstCall).toMatchObject({ page: 0, size: 20 })
  })

  it('산업 필터 적용 → safetyApi.incidents 호출 시 industryCode 포함', async () => {
    incidentsMock.mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    })
    const { wrapper } = await mountView()
    await wrapper.find('[data-testid="incident-industry-select"]').setValue('건설')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const lastCall = incidentsMock.mock.calls[incidentsMock.mock.calls.length - 1][0]
    expect(lastCall).toMatchObject({ page: 0, size: 20, industryCode: '건설' })
  })

  it('반환된 모든 항목을 사고 카드로 렌더링', async () => {
    incidentsMock.mockResolvedValue({
      content: [makeIncident(1), makeIncident(2), makeIncident(3)],
      page: 0,
      size: 20,
      totalElements: 3,
      totalPages: 1,
    })
    const { wrapper } = await mountView()
    const cards = wrapper.findAll('[data-testid="incident-card"]')
    expect(cards.length).toBe(3)
  })
})
