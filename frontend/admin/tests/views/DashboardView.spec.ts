// DashboardView 단위 테스트 — REQ-CMS-001
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createTestingPinia } from '@pinia/testing'

// apiClient mock — useApi composable에서 사용
vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

import DashboardView from '@/views/DashboardView.vue'
import { apiClient } from '@iroum/shared/api/client'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'en',
  messages: {
    ko: {
      dashboard: {
        title: '대시보드',
        userCount: '총 사용자',
        todayLogin: '오늘 로그인',
        systemStatus: '시스템 상태',
        recentActivity: '최근 활동',
        comingSoon: '준비 중',
        statusError: '오류',
      },
      health: { error: '서버 상태 오류' },
    },
    en: {},
  },
})

function mountView() {
  return mount(DashboardView, {
    global: { plugins: [i18n, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('DashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('마운트되며 제목을 표시한다', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { status: 'UP', version: '1.0' } } as never)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('대시보드')
  })

  it('4개의 요약 카드를 렌더링한다', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { status: 'UP', version: '1.0' } } as never)
    const wrapper = mountView()
    await flushPromises()
    // userCount, todayLogin, systemStatus, recentActivity
    expect(wrapper.text()).toContain('총 사용자')
    expect(wrapper.text()).toContain('오늘 로그인')
    expect(wrapper.text()).toContain('시스템 상태')
    expect(wrapper.text()).toContain('최근 활동')
  })

  it('health API 응답 UP 시 상태를 표시한다', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { status: 'UP', version: '1.0.0' } } as never)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('UP')
    expect(wrapper.text()).toContain('1.0.0')
  })

  it('health API 실패 시 오류 alert를 노출한다', async () => {
    vi.mocked(apiClient.get).mockRejectedValueOnce(new Error('Network'))
    const wrapper = mountView()
    await flushPromises()
    // 에러 alert 또는 statusError 텍스트
    expect(
      wrapper.find('[role="alert"]').exists() || wrapper.text().includes('오류'),
    ).toBe(true)
  })

  it('준비 중 placeholder가 일부 카드에 노출된다', async () => {
    vi.mocked(apiClient.get).mockResolvedValueOnce({ data: { status: 'UP', version: '1.0' } } as never)
    const wrapper = mountView()
    await flushPromises()
    // 사용자 수/오늘 로그인/최근 활동 카드 placeholder
    expect(wrapper.text()).toContain('준비 중')
  })
})
