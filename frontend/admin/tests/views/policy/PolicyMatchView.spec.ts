// PolicyMatchView 단위 테스트 — SPEC-CMS-007
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/policy', () => ({
  policyApi: {
    listPrograms: vi.fn().mockResolvedValue({ data: { items: [], total: 0 } }),
    getProgram: vi.fn(),
    match: vi.fn().mockResolvedValue({ data: { results: [] } }),
    getPreferences: vi.fn().mockResolvedValue({ data: null }),
    updatePreferences: vi.fn(),
    listSchedules: vi.fn(),
    syncPrograms: vi.fn(),
    createProgram: vi.fn(),
    updateProgram: vi.fn(),
    deleteProgram: vi.fn(),
    getSchedule: vi.fn(),
    createSchedule: vi.fn(),
    updateSchedule: vi.fn(),
    simulateSchedule: vi.fn(),
    sendSchedule: vi.fn(),
    track: vi.fn(),
  },
}))

import PolicyMatchView from '@/views/policy/PolicyMatchView.vue'

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko: {} } })

function mountView() {
  return mount(PolicyMatchView, {
    global: {
      plugins: [i18n, createTestingPinia({ createSpy: vi.fn, stubActions: false })],
    },
  })
}

describe('PolicyMatchView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('정책 매칭 제목을 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('정책 매칭')
  })

  it('매칭 실행 버튼을 노출한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    const btns = wrapper.findAll('button').map((b) => b.text())
    expect(btns.some((t) => t.includes('매칭 실행'))).toBe(true)
  })

  it('기업 프로필 카드 헤더를 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('기업 프로필')
  })

  it('업종/지역/직원수 폼 항목을 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('업종')
    expect(wrapper.text()).toContain('지역')
    expect(wrapper.text()).toContain('직원수')
  })
})
