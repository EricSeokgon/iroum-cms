// DashboardView 단위 테스트 — REQ-CMS-001
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import { createTestingPinia } from '@pinia/testing'
import ElementPlus from 'element-plus'
import ko from '@/locales/ko.json'
import en from '@/locales/en.json'

vi.mock('@iroum/shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
    interceptors: { request: { use: vi.fn() }, response: { use: vi.fn() } },
  },
  registerAuthHooks: vi.fn(),
}))

vi.mock('@/api/system', () => ({
  dashboard: {
    kpi: vi.fn(),
    trends: vi.fn(),
    topPages: vi.fn(),
  },
  stats: {},
  accessLogs: {},
  codeGroups: {},
  codes: {},
  settings: {},
  maintenance: {},
  auditLogs: {},
}))

import DashboardView from '@/views/DashboardView.vue'
import { dashboard } from '@/api/system'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'en',
  messages: { ko, en },
})

const mockKpi = {
  today_visits: 1500,
  today_unique: 900,
  today_page_views: 4200,
  today_signups: 12,
  error_rate_24h: 0.01,
  avg_response_ms_24h: 250,
  locked_accounts: 0,
  audit_log_24h_count: 100,
  audit_log_critical_24h_count: 0,
  health_status: 'HEALTHY' as const,
}

function mountView() {
  return mount(DashboardView, {
    global: { plugins: [i18n, ElementPlus, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('DashboardView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(dashboard.kpi).mockResolvedValue({ data: mockKpi } as never)
  })

  it('마운트되며 제목을 표시한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('대시보드')
  })

  it('KPI 섹션 카드가 렌더링된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('오늘 현황')
    expect(wrapper.text()).toContain('시스템 상태')
    expect(wrapper.text()).toContain('오늘 방문수')
  })

  it('HEALTHY 상태 시 정상이 표시된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('정상')
  })

  it('health API 실패 시 오류 메시지를 노출한다', async () => {
    vi.mocked(dashboard.kpi).mockRejectedValueOnce(new Error('Network'))
    const wrapper = mountView()
    await flushPromises()
    expect(
      wrapper.find('[role="alert"]').exists() || wrapper.text().includes('불러오지 못했습니다'),
    ).toBe(true)
  })

  it('빠른 메뉴가 노출된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('빠른 메뉴')
  })
})
