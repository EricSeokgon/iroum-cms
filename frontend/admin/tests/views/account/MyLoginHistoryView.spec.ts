// 내 로그인 이력 화면 — Vitest 단위 테스트 (REQ-AUTH-011 사용자 권리)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import MyLoginHistoryView from '@/views/account/MyLoginHistoryView.vue'
import { meApi } from '@/api/me'
import type { PageResponse, LoginHistoryEntry } from '@iroum/shared/types/api'

// meApi mock
vi.mock('@/api/me', () => ({
  meApi: {
    myLoginHistory: vi.fn(),
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
    username: 'me',
    ipAddress: '10.0.0.1',
    userAgent: 'Mozilla/5.0',
    success: true,
    failureReason: undefined,
    createdAt: '2026-04-01T09:00:00Z',
    ...overrides,
  }
}

function pageOf(entries: LoginHistoryEntry[]): PageResponse<LoginHistoryEntry> {
  return { content: entries, page: 0, size: 20, totalElements: entries.length, totalPages: 1 }
}

describe('MyLoginHistoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('헤더 설명 텍스트와 보안 경고를 렌더링한다', async () => {
    vi.mocked(meApi.myLoginHistory).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(MyLoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    // 설명 텍스트
    expect(wrapper.text()).toContain('최근 로그인 시도 기록을 확인하세요')
    // 보안 경고
    expect(wrapper.text()).toContain('비정상적인 로그인 시도가 보이면 즉시 비밀번호를 변경하세요')
  })

  it('빈 상태 메시지를 렌더링한다', async () => {
    vi.mocked(meApi.myLoginHistory).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(MyLoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('로그인 이력이 없습니다')
  })

  it('본인 로그인 이력 항목을 렌더링한다', async () => {
    const entries = [
      makeEntry({ success: true, ipAddress: '192.168.0.1' }),
      makeEntry({ id: 2, success: false, failureReason: 'BAD_CREDENTIALS', ipAddress: '10.0.0.99' }),
    ]
    vi.mocked(meApi.myLoginHistory).mockResolvedValueOnce({ data: pageOf(entries) } as never)

    const wrapper = mount(MyLoginHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('192.168.0.1')
    expect(wrapper.text()).toContain('BAD_CREDENTIALS')
    // 성공/실패 뱃지 모두 존재
    expect(wrapper.find('.bg-green-100').exists()).toBe(true)
    expect(wrapper.find('.bg-red-100').exists()).toBe(true)
  })
})
