// 권한 변경 이력 화면 — Vitest 단위 테스트 (REQ-AUTH-016)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import PermissionChangeHistoryView from '@/views/audit/PermissionChangeHistoryView.vue'
import { auditApi } from '@/api/audit'
import type { PageResponse, PermissionChangeEntry } from '@iroum/shared/types/api'

// auditApi mock
vi.mock('@/api/audit', () => ({
  auditApi: {
    permissionChanges: vi.fn(),
  },
}))

// vue-router mock
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function emptyPage(): PageResponse<PermissionChangeEntry> {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
}

function makeEntry(overrides: Partial<PermissionChangeEntry> = {}): PermissionChangeEntry {
  return {
    id: 1,
    changeType: 'ROLE_ASSIGN',
    targetUserId: 10,
    targetUsername: 'testuser',
    targetRoleCode: 'EDITOR',
    targetResource: 'EDITOR',
    changedBy: 1,
    changedByUsername: 'admin',
    changedAt: '2026-04-01T10:00:00Z',
    severity: 'INFO',
    reason: '테스트 사유',
    ...overrides,
  }
}

function pageOf(entries: PermissionChangeEntry[]): PageResponse<PermissionChangeEntry> {
  return { content: entries, page: 0, size: 20, totalElements: entries.length, totalPages: 1 }
}

describe('PermissionChangeHistoryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('항목이 없을 때 빈 상태 텍스트를 렌더링한다', async () => {
    vi.mocked(auditApi.permissionChanges).mockResolvedValueOnce({ data: emptyPage() } as never)

    const wrapper = mount(PermissionChangeHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('조회된 이력이 없습니다')
  })

  it('항목 목록과 심각도 badge를 렌더링한다', async () => {
    const entries = [
      makeEntry({ severity: 'CRITICAL', changeType: 'ROLE_UNASSIGN' }),
      makeEntry({ id: 2, severity: 'WARN', changeType: 'ROLE_PERMISSION_REVOKE' }),
    ]
    vi.mocked(auditApi.permissionChanges).mockResolvedValueOnce({ data: pageOf(entries) } as never)

    const wrapper = mount(PermissionChangeHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('치명')
    expect(wrapper.text()).toContain('경고')
    expect(wrapper.text()).toContain('testuser')
  })

  it('변경 유형 필터로 검색 시 API 파라미터에 changeType이 포함된다', async () => {
    vi.mocked(auditApi.permissionChanges).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(PermissionChangeHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    // filterChangeType에 값 설정 후 검색
    const vm = wrapper.vm as { filterChangeType: string; onSearch: () => void }
    vm.filterChangeType = 'ROLE_ASSIGN'
    vm.onSearch()
    await flushPromises()

    expect(auditApi.permissionChanges).toHaveBeenCalledWith(
      expect.objectContaining({ changeType: 'ROLE_ASSIGN' }),
    )
  })

  it('날짜 범위가 지정되면 API 파라미터에 from/to가 포함된다', async () => {
    vi.mocked(auditApi.permissionChanges).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(PermissionChangeHistoryView, {
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

    expect(auditApi.permissionChanges).toHaveBeenCalledWith(
      expect.objectContaining({ from: '2026-01-01', to: '2026-12-31' }),
    )
  })

  it('페이지 변경 시 page 파라미터가 갱신된다', async () => {
    vi.mocked(auditApi.permissionChanges).mockResolvedValue({ data: emptyPage() } as never)

    const wrapper = mount(PermissionChangeHistoryView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as { currentPage: number; loadEntries: () => void }
    vm.currentPage = 3
    vm.loadEntries()
    await flushPromises()

    expect(auditApi.permissionChanges).toHaveBeenCalledWith(
      expect.objectContaining({ page: 2 }), // 0-based
    )
  })
})
