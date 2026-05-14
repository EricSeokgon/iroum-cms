// PolicyDetailView 단위 테스트 — SPEC-CMS-007
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/policy', () => ({
  policyApi: {
    listPrograms: vi.fn().mockResolvedValue({ data: { items: [], total: 0 } }),
    getProgram: vi.fn().mockResolvedValue({ data: null }),
    createProgram: vi.fn(),
    updateProgram: vi.fn(),
    deleteProgram: vi.fn(),
    syncPrograms: vi.fn(),
    match: vi.fn(),
    listSchedules: vi.fn(),
    getSchedule: vi.fn(),
    createSchedule: vi.fn(),
    updateSchedule: vi.fn(),
    simulateSchedule: vi.fn(),
    sendSchedule: vi.fn(),
    getPreferences: vi.fn(),
    updatePreferences: vi.fn(),
    track: vi.fn(),
  },
}))

import PolicyDetailView from '@/views/policy/PolicyDetailView.vue'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: {} } })

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', name: 'policy-program-list', component: { template: '<div />' } },
    { path: '/policy/:id', name: 'policy-program-detail', component: PolicyDetailView },
  ],
})

async function mountView(id: string | number = 'new') {
  await router.push({ name: 'policy-program-detail', params: { id: String(id) } })
  await router.isReady()
  return mount(PolicyDetailView, {
    global: {
      plugins: [i18n, router, createTestingPinia({ createSpy: vi.fn, stubActions: false })],
    },
  })
}

describe('PolicyDetailView', () => {
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
    expect(wrapper.text()).toContain('정책사업 등록')
  })

  it('목록 버튼을 노출한다', async () => {
    const wrapper = await mountView('new')
    await flushPromises()
    const btns = wrapper.findAll('button').map((b) => b.text())
    expect(btns.some((t) => t.includes('목록'))).toBe(true)
  })
})
