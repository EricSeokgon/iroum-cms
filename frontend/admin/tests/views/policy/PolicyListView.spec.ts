// PolicyListView 단위 테스트 — SPEC-CMS-007
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

// policyApi 전체를 mock — 스토어 내부의 호출이 모두 통과되도록
vi.mock('@/api/policy', () => ({
  policyApi: {
    listPrograms: vi.fn().mockResolvedValue({ data: { items: [], total: 0 } }),
    getProgram: vi.fn(),
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

import PolicyListView from '@/views/policy/PolicyListView.vue'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: {} } })

const router = createRouter({
  history: createMemoryHistory(),
  routes: [
    { path: '/', component: PolicyListView },
    { path: '/policy/:id', name: 'policy-program-detail', component: { template: '<div />' } },
  ],
})

function mountView() {
  return mount(PolicyListView, {
    global: {
      plugins: [
        i18n,
        router,
        createTestingPinia({ createSpy: vi.fn, stubActions: false }),
      ],
    },
  })
}

describe('PolicyListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = mountView()
    await router.isReady()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('제목을 렌더링한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('정책사업 관리')
  })

  it('필터 입력 필드를 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('상태')
    expect(wrapper.text()).toContain('업종')
    expect(wrapper.text()).toContain('지역')
    expect(wrapper.text()).toContain('검색')
  })

  it('빈 결과 시 안내 텍스트를 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('등록된 정책사업이 없습니다')
  })

  it('검색/초기화 버튼이 존재한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    const btns = wrapper.findAll('button').map((b) => b.text())
    expect(btns.some((t) => t.includes('검색'))).toBe(true)
    expect(btns.some((t) => t.includes('초기화'))).toBe(true)
  })
})
