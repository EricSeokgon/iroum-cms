// IncidentListView 단위 테스트 — SPEC-CMS-006
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/safety', () => ({
  safetyApi: {
    listIncidents: vi.fn().mockResolvedValue({ data: { items: [], total: 0 } }),
    getIncident: vi.fn(),
    createIncident: vi.fn(),
    updateIncident: vi.fn(),
    deleteIncident: vi.fn(),
    syncIncidents: vi.fn(),
  },
}))

import IncidentListView from '@/views/safety/IncidentListView.vue'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: {} } })

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: IncidentListView },
    { path: '/incident/:id', name: 'incident-detail', component: { template: '<div />' } },
  ],
})

function mountView() {
  return mount(IncidentListView, {
    global: {
      plugins: [
        i18n,
        router,
        createTestingPinia({ createSpy: vi.fn, stubActions: false }),
      ],
    },
  })
}

describe('IncidentListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = mountView()
    await router.isReady()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('사고사례 관리 제목을 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('사고사례 관리')
  })

  it('업종/사고 유형/중증도 필터를 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('업종')
    expect(wrapper.text()).toContain('사고 유형')
    expect(wrapper.text()).toContain('중증도')
  })

  it('검색/초기화 버튼이 존재한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    const btns = wrapper.findAll('button').map((b) => b.text())
    expect(btns.some((t) => t.includes('검색'))).toBe(true)
    expect(btns.some((t) => t.includes('초기화'))).toBe(true)
  })
})
