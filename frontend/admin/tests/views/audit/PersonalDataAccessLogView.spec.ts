// 회원정보 접근 이력 화면 (관리자) — Vitest 단위 테스트 (REQ-AUTH-018)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import PersonalDataAccessLogView from '@/views/audit/PersonalDataAccessLogView.vue'
import { auditApi } from '@/api/audit'
import type { PageResponse, PersonalDataAccessEntry } from '@iroum/shared/types/api'

// auditApi mock
vi.mock('@/api/audit', () => ({
  auditApi: {
    permissionChanges: vi.fn(),
    personalDataAccess: vi.fn(),
  },
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function emptyPage(): PageResponse<PersonalDataAccessEntry> {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

function makeEntry(overrides: Partial<PersonalDataAccessEntry> = {}): PersonalDataAccessEntry {
  return {
    id: 1,
    viewerId: 1,
    viewerUsername: 'admin',
    viewerRole: 'SUPER_ADMIN',
    targetUserId: 10,
    targetUsername: 'testuser',
    accessedFields: ['email', 'name'],
    purpose: 'ADMIN_USER_LIST',
    ipAddress: '127.0.0.1',
    userAgent: 'Mozilla/5.0',
    accessedAt: '2026-04-01T10:00:00Z',
    ...overrides,
  }
}

function pageOf(entries: PersonalDataAccessEntry[]): PageResponse<PersonalDataAccessEntry> {
  return { content: entries, page: 0, size: 20, totalElements: entries.length, totalPages: 1 }
}

describe('PersonalDataAccessLogView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('항목이 없을 때 빈 상태 텍스트를 렌더링한다', async () => {
    vi.mocked(auditApi.personalDataAccess).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(PersonalDataAccessLogView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('조회된 이력이 없습니다')
  })

  it('접근 이력 항목과 필드 태그를 렌더링한다', async () => {
    const entries = [
      makeEntry({ accessedFields: ['email', 'name', 'phone'] }),
      makeEntry({ id: 2, viewerUsername: 'editor', accessedFields: ['email'] }),
    ]
    vi.mocked(auditApi.personalDataAccess).mockResolvedValueOnce({ data: pageOf(entries) } as never)

    const wrapper = mount(PersonalDataAccessLogView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('testuser')
    expect(wrapper.text()).toContain('email')
    expect(wrapper.text()).toContain('editor')
  })

  it('목적 필터로 검색 시 API 파라미터에 purpose가 포함된다', async () => {
    vi.mocked(auditApi.personalDataAccess).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(PersonalDataAccessLogView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as { filterPurpose: string; onSearch: () => void }
    vm.filterPurpose = 'SUPPORT'
    vm.onSearch()
    await flushPromises()

    expect(auditApi.personalDataAccess).toHaveBeenCalledWith(
      expect.objectContaining({ purpose: 'SUPPORT' }),
    )
  })

  it('날짜 범위가 지정되면 API 파라미터에 from/to가 포함된다', async () => {
    vi.mocked(auditApi.personalDataAccess).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(PersonalDataAccessLogView, {
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

    expect(auditApi.personalDataAccess).toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-01-01', to: '2026-12-31' }),
    )
  })

  it('행 클릭 시 상세 모달이 열린다', async () => {
    const entry = makeEntry()
    vi.mocked(auditApi.personalDataAccess).mockResolvedValueOnce({ data: pageOf([entry]) } as never)

    const wrapper = mount(PersonalDataAccessLogView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as { openDetail: (row: PersonalDataAccessEntry) => void; showDetail: boolean }
    vm.openDetail(entry)
    await flushPromises()

    expect(vm.showDetail).toBe(true)
  })
})
