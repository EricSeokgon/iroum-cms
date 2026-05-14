// SPEC-CMS-PUBLIC-001 T-010 — MaintenanceView (F-04) 검증
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const healthMock = vi.fn()
vi.mock('@/api/systemApi', () => ({
  systemApi: {
    health: (...args: unknown[]) => healthMock(...args),
  },
}))

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div />' } },
      {
        path: '/maintenance',
        name: 'maintenance',
        component: () => import('@/views/MaintenanceView.vue'),
      },
    ],
  })
  router.push('/maintenance')
  await router.isReady()
  const MaintenanceView = (await import('@/views/MaintenanceView.vue')).default
  const wrapper = mount(MaintenanceView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('MaintenanceView — F-04 자동 리프레시', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    healthMock.mockReset()
    healthMock.mockResolvedValue({ status: 'UP', maintenanceMode: true, reason: '정기 점검' })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('마운트 시 maintenanceStore.checkMaintenance 가 즉시 1회 호출된다', async () => {
    await mountView()
    expect(healthMock).toHaveBeenCalledTimes(1)
  })

  it('5분 후 자동으로 checkMaintenance 가 재호출된다', async () => {
    vi.useFakeTimers()
    await mountView()
    // mount 시 즉시 1회 호출
    expect(healthMock).toHaveBeenCalledTimes(1)
    // 5분 경과
    await vi.advanceTimersByTimeAsync(300_000)
    expect(healthMock).toHaveBeenCalledTimes(2)
  })

  it('maintenanceMode=false 응답 시 자동으로 홈으로 replace 된다', async () => {
    healthMock.mockResolvedValue({ status: 'UP', maintenanceMode: false })
    const { router } = await mountView()
    await flushPromises()
    expect(router.currentRoute.value.name).toBe('home')
  })

  it('점검 메시지(reason)와 종료 시각(until)이 렌더링된다', async () => {
    healthMock.mockResolvedValue({
      status: 'UP',
      maintenanceMode: true,
      reason: '정기 점검 작업 중',
      until: '2026-05-14T22:00:00+09:00',
    })
    const { wrapper } = await mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('정기 점검 작업 중')
    expect(wrapper.text()).toContain('2026-05-14T22:00:00+09:00')
  })

  it('컴포넌트 unmount 시 setInterval 이 정리된다 (메모리 누수 방지)', async () => {
    vi.useFakeTimers()
    const clearSpy = vi.spyOn(globalThis, 'clearInterval')
    const { wrapper } = await mountView()
    wrapper.unmount()
    expect(clearSpy).toHaveBeenCalled()
    clearSpy.mockRestore()
  })
})
