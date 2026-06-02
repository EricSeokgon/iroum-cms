// SPEC-CMS-DASHBOARD-REFRESH-001 — 자동 새로고침 인디케이터 컴포넌트 테스트
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import DashboardRefreshIndicator from '@/components/dashboard/DashboardRefreshIndicator.vue'

describe('DashboardRefreshIndicator — SPEC-CMS-DASHBOARD-REFRESH-001', () => {
  it('intervalSeconds 가 null 이면 아무것도 렌더링하지 않는다 (자동 새로고침 OFF)', () => {
    const wrapper = mount(DashboardRefreshIndicator, {
      props: { secondsRemaining: 0, intervalSeconds: null },
    })
    expect(wrapper.find('[data-testid="refresh-indicator"]').exists()).toBe(false)
  })

  it('intervalSeconds 가 있으면 남은 초 카운트다운을 표시한다', () => {
    const wrapper = mount(DashboardRefreshIndicator, {
      props: { secondsRemaining: 25, intervalSeconds: 30 },
    })
    const el = wrapper.find('[data-testid="refresh-indicator"]')
    expect(el.exists()).toBe(true)
    expect(el.text()).toContain('25')
    expect(el.text()).toContain('다음 새로고침')
  })

  it('"지금 새로고침" 클릭 시 refresh 이벤트를 emit 한다', async () => {
    const wrapper = mount(DashboardRefreshIndicator, {
      props: { secondsRemaining: 10, intervalSeconds: 30 },
    })
    const btn = wrapper.find('[data-testid="refresh-now"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    expect(wrapper.emitted('refresh')).toBeTruthy()
    expect(wrapper.emitted('refresh')).toHaveLength(1)
  })
})
