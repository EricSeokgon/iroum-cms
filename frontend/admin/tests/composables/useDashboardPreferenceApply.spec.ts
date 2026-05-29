// SPEC-CMS-DASHBOARD-PERSONALIZE-001 — preference 자동 적용 composable 테스트
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'

vi.mock('@/api/dashboardPreference', () => ({
  dashboardPreferenceApi: {
    get: vi.fn(),
    patch: vi.fn(),
    reset: vi.fn(),
    toggleVisibility: vi.fn(),
    showAllWidgets: vi.fn(),
    patchPositions: vi.fn(),
  },
}))

import { useDashboardPreferenceApply } from '@/composables/useDashboardPreferenceApply'
import { useDashboardPreferenceStore } from '@/stores/dashboardPreferenceStore'

// composable 을 호출하는 더미 컴포넌트
const Host = defineComponent({
  setup() {
    useDashboardPreferenceApply()
    return () => h('div')
  },
})

describe('useDashboardPreferenceApply — SPEC-CMS-DASHBOARD-PERSONALIZE-001', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    // <html> 데이터셋 초기화
    delete document.documentElement.dataset.theme
    delete document.documentElement.dataset.density
    delete document.documentElement.dataset.fontScale
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('AC-DP-002-1: theme=DARK 이면 <html data-theme="dark"> 적용', async () => {
    const store = useDashboardPreferenceStore()
    store.preference.theme = 'DARK'

    const wrapper = mount(Host)

    expect(document.documentElement.dataset.theme).toBe('dark')
    wrapper.unmount()
  })

  it('AC-DP-002-1: theme=LIGHT 이면 <html data-theme="light"> 적용', () => {
    const store = useDashboardPreferenceStore()
    store.preference.theme = 'LIGHT'

    const wrapper = mount(Host)

    expect(document.documentElement.dataset.theme).toBe('light')
    wrapper.unmount()
  })

  it('AC-DP-002-2: theme=SYSTEM 이면 matchMedia 결과 반영 + change 리스너 등록', () => {
    const addEventListener = vi.fn()
    Object.defineProperty(window, 'matchMedia', {
      value: vi.fn().mockReturnValue({
        matches: true,
        media: '(prefers-color-scheme: dark)',
        addEventListener,
        removeEventListener: vi.fn(),
      }),
      writable: true,
      configurable: true,
    })

    const store = useDashboardPreferenceStore()
    store.preference.theme = 'SYSTEM'

    const wrapper = mount(Host)

    expect(document.documentElement.dataset.theme).toBe('dark')
    expect(addEventListener).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('AC-DP-002-3: density=COMPACT, font_scale=0.875 → <html> 데이터셋 동기화', () => {
    const store = useDashboardPreferenceStore()
    store.preference.density = 'COMPACT'
    store.preference.font_scale = 0.875

    const wrapper = mount(Host)

    expect(document.documentElement.dataset.density).toBe('COMPACT')
    expect(document.documentElement.dataset.fontScale).toBe('0.875')
    wrapper.unmount()
  })

  it('theme 이 LIGHT → SYSTEM 으로 변경되면 matchMedia 리스너가 등록된다', async () => {
    const addEventListener = vi.fn()
    Object.defineProperty(window, 'matchMedia', {
      value: vi.fn().mockReturnValue({
        matches: false,
        media: '(prefers-color-scheme: dark)',
        addEventListener,
        removeEventListener: vi.fn(),
      }),
      writable: true,
      configurable: true,
    })

    const store = useDashboardPreferenceStore()
    store.preference.theme = 'LIGHT'
    const wrapper = mount(Host)
    expect(addEventListener).not.toHaveBeenCalled()

    store.preference.theme = 'SYSTEM'
    await wrapper.vm.$nextTick()

    expect(addEventListener).toHaveBeenCalled()
    wrapper.unmount()
  })

  it('컴포넌트 unmount 시 matchMedia 리스너가 해제된다 (memory leak 방지)', async () => {
    const removeEventListener = vi.fn()
    Object.defineProperty(window, 'matchMedia', {
      value: vi.fn().mockReturnValue({
        matches: false,
        media: '(prefers-color-scheme: dark)',
        addEventListener: vi.fn(),
        removeEventListener,
      }),
      writable: true,
      configurable: true,
    })

    const store = useDashboardPreferenceStore()
    store.preference.theme = 'SYSTEM'

    const wrapper = mount(Host)
    wrapper.unmount()

    expect(removeEventListener).toHaveBeenCalled()
  })
})
