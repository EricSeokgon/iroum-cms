/**
 * ApprovalQueueView 단위 테스트 — SPEC-CMS-USER-APPROVAL-001 T8/T10
 * 대기열 목록/승인/거절 다이얼로그 UI 검증.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import ApprovalQueueView from '../../../src/views/users/ApprovalQueueView.vue'
import type { PageResponse, PendingUser } from '../../../src/api/userApprovals'

vi.mock('../../../src/api/userApprovals', () => ({
  userApprovalsApi: {
    list: vi.fn(),
    detail: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
    bulkApprove: vi.fn(),
    bulkReject: vi.fn(),
  },
}))

import { userApprovalsApi } from '../../../src/api/userApprovals'

const mockList = vi.mocked(userApprovalsApi.list)
const mockApprove = vi.mocked(userApprovalsApi.approve)
const mockReject = vi.mocked(userApprovalsApi.reject)

const pending: PendingUser[] = [
  { userId: 10, username: 'a@example.com', email: 'a@example.com', name: '대기자A', createdAt: '2026-06-01T00:00:00Z', organizationId: null },
  { userId: 11, username: 'b@example.com', email: 'b@example.com', name: '대기자B', createdAt: '2026-06-02T00:00:00Z', organizationId: null },
]

const emptyPage: PageResponse<PendingUser> = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const filledPage: PageResponse<PendingUser> = { content: pending, page: 0, size: 20, totalElements: 2, totalPages: 1 }

const i18n = createI18n({ legacy: false, locale: 'ko', fallbackLocale: 'en', messages: { ko: {}, en: {} } })
const router = createRouter({
  history: createWebHistory(),
  routes: [{ path: '/', component: { template: '<div />' } }],
})

function mountView() {
  return mount(ApprovalQueueView, {
    global: { plugins: [ElementPlus, createPinia(), i18n, router] },
  })
}

describe('ApprovalQueueView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('빈 대기열이면 안내 문구를 표시한다', async () => {
    mockList.mockResolvedValue({ data: emptyPage } as never)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('승인 대기 중인 가입자가 없습니다')
  })

  it('대기 사용자 목록을 렌더링한다', async () => {
    mockList.mockResolvedValue({ data: filledPage } as never)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('대기자A')
    expect(wrapper.text()).toContain('대기자B')
    expect(wrapper.text()).toContain('a@example.com')
  })

  it('단건 승인 버튼 클릭 시 approve API를 호출한다', async () => {
    mockList.mockResolvedValue({ data: filledPage } as never)
    mockApprove.mockResolvedValue({ data: undefined } as never)
    const wrapper = mountView()
    await flushPromises()

    await wrapper.find('[data-testid="approve-10"]').trigger('click')
    await flushPromises()

    expect(mockApprove).toHaveBeenCalledWith(10)
  })

  it('거절 다이얼로그에서 사유 입력 후 reject API를 호출한다', async () => {
    mockList.mockResolvedValue({ data: filledPage } as never)
    mockReject.mockResolvedValue({ data: undefined } as never)
    const wrapper = mountView()
    await flushPromises()

    // 거절 버튼 → 다이얼로그 오픈
    await wrapper.find('[data-testid="reject-11"]').trigger('click')
    await flushPromises()

    // 컴포넌트 인스턴스로 사유 주입 후 확정 (다이얼로그 teleport 회피)
    const vm = wrapper.vm as unknown as { rejectReason: string; confirmReject: () => Promise<void> }
    vm.rejectReason = '자격 미달'
    await vm.confirmReject()
    await flushPromises()

    expect(mockReject).toHaveBeenCalledWith(11, '자격 미달')
  })

  it('검색어 입력 후 검색 시 keyword 파라미터로 재조회한다', async () => {
    mockList.mockResolvedValue({ data: filledPage } as never)
    const wrapper = mountView()
    await flushPromises()
    mockList.mockClear()

    const vm = wrapper.vm as unknown as { keyword: string; reload: () => void }
    vm.keyword = '대기자A'
    vm.reload()
    await flushPromises()

    expect(mockList).toHaveBeenCalledWith(expect.objectContaining({ keyword: '대기자A' }))
  })
})
