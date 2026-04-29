// 로그인 이력 화면 — Vitest 단위 테스트 (REQ-AUTH-011)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import LoginHistoryView from '@/views/audit/LoginHistoryView.vue'
import { auditApi } from '@/api/audit'
import type { PageResponse, LoginHistoryEntry } from '@iroum/shared/types/api'

// auditApi mock
vi.mock('@/api/audit', () => ({
  auditApi: {
    loginHistory: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function emptyPage(): PageResponse<LoginHistoryEntry> {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

function makeEntry(overrides: Partial<LoginHistoryEntry> = {}): LoginHistoryEntry {
  return {
    id: 1,
    userId: 10,
    username: 'testuser',
    ipAddress: '192.168.1.1',
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)',
    success: true,
    failureReason: undefined,
    createdAt: '2026-04-01T10:00:00Z',
    ...overrides,
  }
}

function pageOf(entries: LoginHistoryEntry[]): PageResponse<LoginHistoryEntry> {
  return { content: entries, page: 0, size: 20, totalElements: entries.length, totalPages: 1 }
}

describe('LoginHistoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('항목이 없을 때 빈 상태 텍스트를 렌더링한다', async () => {
    vi.mocked(auditApi.loginHistory).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(LoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('조회된 이력이 없습니다')
  })

  it('성공 항목에 초록 뱃지(✓ 성공)를 렌더링한다', async () => {
    const entries = [makeEntry({ success: true })]
    vi.mocked(auditApi.loginHistory).mockResolvedValueOnce({ data: pageOf(entries) } as never)

    const wrapper = mount(LoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('✓')
    expect(wrapper.text()).toContain('성공')
    // 성공 뱃지 클래스 확인
    const successBadge = wrapper.find('.bg-green-100')
    expect(successBadge.exists()).toBe(true)
  })

  it('실패 항목에 빨간 뱃지(✗ 실패)를 렌더링한다', async () => {
    const entries = [makeEntry({ success: false, failureReason: 'INVALID_CREDENTIALS' })]
    vi.mocked(auditApi.loginHistory).mockResolvedValueOnce({ data: pageOf(entries) } as never)

    const wrapper = mount(LoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('✗')
    expect(wrapper.text()).toContain('실패')
    expect(wrapper.text()).toContain('INVALID_CREDENTIALS')
    const failBadge = wrapper.find('.bg-red-100')
    expect(failBadge.exists()).toBe(true)
  })

  it('사용자명 필터로 검색 시 API 파라미터에 username이 포함된다', async () => {
    vi.mocked(auditApi.loginHistory).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(LoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as { filterUsername: string; onSearch: () => void }
    vm.filterUsername = 'admin'
    vm.onSearch()
    await flushPromises()

    expect(auditApi.loginHistory).toHaveBeenCalledWith(
      expect.objectContaining({ username: 'admin' }),
    )
  })

  it('날짜 범위 필터로 검색 시 from/to 파라미터가 포함된다', async () => {
    vi.mocked(auditApi.loginHistory).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(LoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      filterDateRange: [string, string] | null
      onSearch: () => void
    }
    vm.filterDateRange = ['2026-01-01', '2026-12-31']
    vm.onSearch()
    await flushPromises()

    expect(auditApi.loginHistory).toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-01-01', to: '2026-12-31' }),
    )
  })
})
