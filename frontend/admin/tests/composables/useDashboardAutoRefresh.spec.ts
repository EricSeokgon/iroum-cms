// SPEC-CMS-DASHBOARD-REFRESH-001 — 자동 새로고침 타이머 composable 단위 테스트
// REQ-REFRESH-002(카운트다운) / 003(Visibility 일시정지·재개) / 004(언마운트 정리)
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { defineComponent, h, ref, type Ref } from 'vue'
import { mount } from '@vue/test-utils'

import { useDashboardAutoRefresh } from '@/composables/useDashboardAutoRefresh'

// composable 을 호출하는 더미 호스트 — secondsRemaining/forceRefresh 를 외부로 노출
function createHost(
  interval: Ref<number | null>,
  onTick: () => Promise<void>,
) {
  let api: ReturnType<typeof useDashboardAutoRefresh> | null = null
  const Host = defineComponent({
    setup() {
      api = useDashboardAutoRefresh(interval, onTick)
      return () => h('div')
    },
  })
  const wrapper = mount(Host)
  return { wrapper, get api() {
    if (!api) throw new Error('composable not initialized')
    return api
  } }
}

// document.visibilityState 를 조작하는 헬퍼
function setVisibility(state: 'visible' | 'hidden'): void {
  Object.defineProperty(document, 'visibilityState', {
    value: state,
    configurable: true,
  })
  document.dispatchEvent(new Event('visibilitychange'))
}

describe('useDashboardAutoRefresh — SPEC-CMS-DASHBOARD-REFRESH-001', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    setVisibility('visible')
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('refreshIntervalSeconds 가 null→값 으로 바뀌면 타이머가 시작된다', async () => {
    const interval = ref<number | null>(null)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)

    expect(api.secondsRemaining.value).toBe(0)

    interval.value = 30
    await wrapper.vm.$nextTick()

    expect(api.secondsRemaining.value).toBe(30)
    wrapper.unmount()
  })

  it('매 초마다 secondsRemaining 을 1씩 감소시킨다', async () => {
    const interval = ref<number | null>(10)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    expect(api.secondsRemaining.value).toBe(10)
    await vi.advanceTimersByTimeAsync(1000)
    expect(api.secondsRemaining.value).toBe(9)
    await vi.advanceTimersByTimeAsync(2000)
    expect(api.secondsRemaining.value).toBe(7)
    wrapper.unmount()
  })

  it('카운트다운이 0에 도달하면 onTick 호출 후 interval 로 재설정한다', async () => {
    const interval = ref<number | null>(3)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(3000)

    expect(onTick).toHaveBeenCalledTimes(1)
    // 0 도달 직후 interval 값으로 리셋
    expect(api.secondsRemaining.value).toBe(3)
    wrapper.unmount()
  })

  it('forceRefresh 는 즉시 onTick 을 호출하고 카운트다운을 리셋한다', async () => {
    const interval = ref<number | null>(60)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(5000)
    expect(api.secondsRemaining.value).toBe(55)

    await api.forceRefresh()

    expect(onTick).toHaveBeenCalledTimes(1)
    expect(api.secondsRemaining.value).toBe(60)
    wrapper.unmount()
  })

  it('탭이 hidden 으로 바뀌면 카운트다운을 일시정지한다 (REQ-REFRESH-003)', async () => {
    const interval = ref<number | null>(30)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(5000)
    expect(api.secondsRemaining.value).toBe(25)

    setVisibility('hidden')
    await vi.advanceTimersByTimeAsync(10_000)

    // hidden 동안에는 감소하지 않음
    expect(api.secondsRemaining.value).toBe(25)
    expect(onTick).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('hidden 동안 interval 이상 경과 후 visible 복귀하면 즉시 새로고침한다 (REQ-REFRESH-003)', async () => {
    const interval = ref<number | null>(30)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    setVisibility('hidden')
    // 실제 시간 경과를 흉내 — Date.now 진행
    vi.setSystemTime(Date.now() + 31_000)
    setVisibility('visible')
    await wrapper.vm.$nextTick()
    await Promise.resolve()

    expect(onTick).toHaveBeenCalledTimes(1)
    expect(api.secondsRemaining.value).toBe(30)
    wrapper.unmount()
  })

  it('언마운트 시 setInterval 과 이벤트 리스너를 모두 정리한다 (REQ-REFRESH-004)', async () => {
    const removeSpy = vi.spyOn(document, 'removeEventListener')
    const clearSpy = vi.spyOn(globalThis, 'clearInterval')

    const interval = ref<number | null>(30)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    wrapper.unmount()

    expect(removeSpy).toHaveBeenCalledWith('visibilitychange', expect.any(Function))
    expect(clearSpy).toHaveBeenCalled()

    // 언마운트 후 타이머가 더 이상 동작하지 않음
    await vi.advanceTimersByTimeAsync(40_000)
    expect(onTick).not.toHaveBeenCalled()
  })

  it('refreshIntervalSeconds 가 null 이면 타이머를 만들지 않고 secondsRemaining=0', async () => {
    const interval = ref<number | null>(null)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(60_000)

    expect(api.secondsRemaining.value).toBe(0)
    expect(onTick).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('값→null 로 바뀌면 진행 중이던 타이머를 중지한다', async () => {
    const interval = ref<number | null>(30)
    const onTick = vi.fn().mockResolvedValue(undefined)
    const { wrapper, api } = createHost(interval, onTick)
    await wrapper.vm.$nextTick()

    await vi.advanceTimersByTimeAsync(5000)
    expect(api.secondsRemaining.value).toBe(25)

    interval.value = null
    await wrapper.vm.$nextTick()

    expect(api.secondsRemaining.value).toBe(0)
    await vi.advanceTimersByTimeAsync(60_000)
    expect(onTick).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
