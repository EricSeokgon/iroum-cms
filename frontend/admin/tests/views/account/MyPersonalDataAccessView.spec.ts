// 내 회원정보 접근 이력 화면 — Vitest 단위 테스트 (REQ-AUTH-018 사용자 권리)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import MyPersonalDataAccessView from '@/views/account/MyPersonalDataAccessView.vue'
import { meApi } from '@/api/me'
import type { PageResponse, PersonalDataAccessEntry } from '@iroum/shared/types/api'

// meApi mock
vi.mock('@/api/me', () => ({
  meApi: {
    myPersonalDataAccess: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function emptyPage(): PageResponse<PersonalDataAccessEntry> {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

function makeEntry(overrides: Partial<PersonalDataAccessEntry> = {}): PersonalDataAccessEntry {
  return {
    id: 1,
    viewerId: 99,
    viewerUsername: 'another_admin',
    viewerRole: 'DEPT_ADMIN',
    targetUserId: 10,
    targetUsername: 'me',
    accessedFields: ['email', 'name'],
    purpose: 'ADMIN_USER_LIST',
    ipAddress: '10.0.0.1',
    userAgent: 'Mozilla/5.0',
    accessedAt: '2026-04-01T09:00:00Z',
    ...overrides,
  }
}

function pageOf(entries: PersonalDataAccessEntry[]): PageResponse<PersonalDataAccessEntry> {
  return { content: entries, page: 0, size: 20, totalElements: entries.length, totalPages: 1 }
}

describe('MyPersonalDataAccessView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('설명 텍스트와 보안 경고를 렌더링한다', async () => {
    vi.mocked(meApi.myPersonalDataAccess).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(MyPersonalDataAccessView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    // 설명 텍스트
    expect(wrapper.text()).toContain('누가 내 정보를 조회했는지 확인')
    // 보안 경고
    expect(wrapper.text()).toContain('비정상적인 접근이 의심되면')
  })

  it('본인 접근 이력 항목을 렌더링한다', async () => {
    const entries = [
      makeEntry({ viewerUsername: 'admin_user', accessedFields: ['email', 'phone'] }),
    ]
    vi.mocked(meApi.myPersonalDataAccess).mockResolvedValueOnce({ data: pageOf(entries) } as never)

    const wrapper = mount(MyPersonalDataAccessView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('admin_user')
    expect(wrapper.text()).toContain('email')
  })

  it('데이터가 없을 때 빈 상태 메시지를 표시한다', async () => {
    vi.mocked(meApi.myPersonalDataAccess).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(MyPersonalDataAccessView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('아직 다른 사용자가 회원님의 정보를 조회한 기록이 없습니다')
  })
})
