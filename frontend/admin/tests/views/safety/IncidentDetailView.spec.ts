// IncidentDetailView 단위 테스트 — SPEC-CMS-006
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/safety', () => ({
  safetyApi: {
    listIncidents: vi.fn(),
    getIncident: vi.fn().mockResolvedValue({ data: null }),
    createIncident: vi.fn(),
    updateIncident: vi.fn(),
    deleteIncident: vi.fn(),
    syncIncidents: vi.fn(),
  },
}))

import IncidentDetailView from '@/views/safety/IncidentDetailView.vue'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: {} } })

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'incident-list', component: { template: '<div />' } },
    { path: '/incident/:id', name: 'incident-detail', component: IncidentDetailView },
  ],
})

async function mountView(id: string = 'new') {
  await router.push({ name: 'incident-detail', params: { id } })
  await router.isReady()
  return mount(IncidentDetailView, {
    global: {
      plugins: [
        i18n,
        router,
        createTestingPinia({ createSpy: vi.fn, stubActions: false }),
      ],
    },
  })
}

describe('IncidentDetailView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = await mountView('new')
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('신규 모드 시 등록 제목을 표시한다', async () => {
    const wrapper = await mountView('new')
    await flushPromises()
    expect(wrapper.text()).toContain('사고사례 등록')
  })

  it('목록 버튼을 노출한다', async () => {
    const wrapper = await mountView('new')
    await flushPromises()
    const btns = wrapper.findAll('button').map((b) => b.text())
    expect(btns.some((t) => t.includes('목록'))).toBe(true)
  })
})
