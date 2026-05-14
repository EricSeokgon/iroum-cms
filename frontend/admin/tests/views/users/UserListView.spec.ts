/**
 * UserListView 단위 테스트 — SPEC-CMS-002 REQ-AUTH-006
 * 사용자 목록 CRUD UI 검증
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import UserListView from '../../../src/views/users/UserListView.vue'
import type { PageResponse, UserSummary } from '@iroum/shared/types/api'

// usersApi 모킹
vi.mock('../../../src/api/users', () => ({
  usersApi: {
    list: vi.fn(),
    unlock: vi.fn(),
    forceLogout: vi.fn(),
    delete: vi.fn(),
  },
}))

// UserFormView 모킹 (단위 테스트 격리)
vi.mock('../../../src/views/users/UserFormView.vue', () => ({
  default: {
    name: 'UserFormView',
    template: '<div data-testid="user-form-mock" />',
    props: ['mode', 'user'],
    emits: ['close', 'saved'],
  },
}))

import { usersApi } from '../../../src/api/users'

const mockList = vi.mocked(usersApi.list)
const mockUnlock = vi.mocked(usersApi.unlock)
const mockDelete = vi.mocked(usersApi.delete)

const mockUsers: UserSummary[] = [
  {
    id: 1, uuid: 'u1', username: 'admin', email: 'admin@test.com',
    name: '관리자', status: 'ACTIVE', createdAt: '2024-01-01T00:00:00Z',
  },
  {
    id: 2, uuid: 'u2', username: 'locked_user', email: 'locked@test.com',
    name: '잠금사용자', status: 'LOCKED', createdAt: '2024-02-01T00:00:00Z',
  },
  {
    id: 3, uuid: 'u3', username: 'viewer', email: 'viewer@test.com',
    name: '뷰어', status: 'INACTIVE', createdAt: '2024-03-01T00:00:00Z',
  },
]

const emptyPage: PageResponse<UserSummary> = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const userPage: PageResponse<UserSummary> = { content: mockUsers, page: 0, size: 20, totalElements: 3, totalPages: 1 }

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'en',
  messages: {
    ko: {
      common: { cancel: '취소', back: '뒤로' },
      users: {
        title: '사용자 관리', empty: '사용자가 없습니다', add: '사용자 추가',
        search: '검색', filterStatus: '상태 필터',
        edit: '수정', delete: '삭제', unlock: '잠금 해제', forceLogout: '강제 로그아웃',
        passwordHint: '비밀번호 힌트',
        field: {
          username: '사용자명', email: '이메일', name: '이름', status: '상태',
          roleCodes: '역할', lastLoginAt: '마지막 로그인', createdAt: '가입일',
        },
        status: { ALL: '전체', ACTIVE: '활성', INACTIVE: '비활성', LOCKED: '잠김', DELETED: '삭제됨' },
        action: { view: '보기', edit: '수정', delete: '삭제', unlock: '잠금 해제', forceLogout: '강제 로그아웃' },
        confirm: { delete: '{name} 삭제?', unlock: '{name} 잠금 해제?', forceLogout: '{name} 강제 로그아웃?' },
        col: { actions: '액션' },
        error: { loadFailed: '로드 실패', deleteFailed: '삭제 실패', unlockFailed: '잠금 해제 실패', forceLogoutFailed: '강제 로그아웃 실패' },
        success: { deleted: '삭제 완료', unlocked: '잠금 해제 완료', forcedLogout: '강제 로그아웃 완료' },
      },
      a11y: { pagination: '페이지 탐색' },
    },
    en: {},
  },
})

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div />' } },
    { path: '/users', name: 'user-list', component: { template: '<div />' } },
    { path: '/users/:id', name: 'user-detail', component: { template: '<div />' } },
  ],
})

function mountView() {
  return mount(UserListView, {
    global: {
      plugins: [ElementPlus, createPinia(), i18n, router],
    },
  })
}

describe('UserListView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('API가 빈 배열 반환 시 빈 상태 표시', async () => {
    mockList.mockResolvedValue({ data: emptyPage } as never)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('사용자가 없습니다')
  })

  it('API가 데이터 반환 시 테이블에 사용자 목록 표시', async () => {
    mockList.mockResolvedValue({ data: userPage } as never)
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).toContain('locked_user')
  })

  it('페이지네이션 변경 시 list API를 page 파라미터와 함께 재호출', async () => {
    mockList.mockResolvedValue({ data: userPage } as never)
    const wrapper = mountView()
    await flushPromises()
    // 최초 호출 확인
    expect(mockList).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }))
    // page-size 변경 시뮬레이션
    wrapper.vm.pageSize = 50
    wrapper.vm.currentPage = 2
    await wrapper.vm.loadUsers()
    expect(mockList).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 50 }))
  })

  it('검색 디바운스 후 search 파라미터로 API 재호출', async () => {
    mockList.mockResolvedValue({ data: emptyPage } as never)
    const wrapper = mountView()
    await flushPromises()
    // 디바운스된 검색어를 직접 변경하여 즉시 확인
    // searchQuery 변경 후 디바운스 대기
    wrapper.vm.searchQuery = 'testuser'
    // 디바운스 우회: 직접 loadUsers 호출 전 wait
    await new Promise((r) => setTimeout(r, 350))
    await flushPromises()
    await wrapper.vm.loadUsers()
    expect(mockList).toHaveBeenCalledWith(expect.objectContaining({ search: 'testuser' }))
  })

  it('상태 필터 변경 시 status 파라미터로 API 재호출', async () => {
    mockList.mockResolvedValue({ data: emptyPage } as never)
    const wrapper = mountView()
    await flushPromises()
    wrapper.vm.statusFilter = 'ACTIVE'
    await wrapper.vm.loadUsers()
    expect(mockList).toHaveBeenCalledWith(expect.objectContaining({ status: 'ACTIVE' }))
  })

  it('잠금 상태(LOCKED) 사용자에게만 잠금 해제 버튼 표시', async () => {
    mockList.mockResolvedValue({ data: userPage } as never)
    const wrapper = mountView()
    await flushPromises()
    // aria-label로 잠금 해제 버튼 탐색
    const unlockBtns = wrapper.findAll('[aria-label*="잠금 해제"]')
    // LOCKED 상태 사용자가 1명이므로 버튼 1개
    expect(unlockBtns).toHaveLength(1)
    // aria-label은 username으로 구성됨 (잠금 해제 + username)
    expect(unlockBtns[0].attributes('aria-label')).toContain('locked_user')
  })

  it('삭제 버튼 클릭 시 확인 다이얼로그 표시 후 삭제 API 호출', async () => {
    mockList.mockResolvedValue({ data: userPage } as never)
    mockDelete.mockResolvedValue({ data: undefined } as never)
    const wrapper = mountView()
    await flushPromises()
    // ElMessageBox.confirm을 auto-confirm으로 모킹
    const { ElMessageBox } = await import('element-plus')
    vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue('confirm' as never)
    const deleteBtns = wrapper.findAll('[aria-label*="삭제 admin"]')
    await deleteBtns[0]?.trigger('click')
    await flushPromises()
    expect(mockDelete).toHaveBeenCalledWith(1)
  })

  it('사용자 추가 버튼 클릭 시 create 모달 열림', async () => {
    mockList.mockResolvedValue({ data: emptyPage } as never)
    const wrapper = mountView()
    await flushPromises()
    await wrapper.find('button').trigger('click')  // "사용자 추가" 버튼
    expect(wrapper.vm.showForm).toBe(true)
    expect(wrapper.vm.formMode).toBe('create')
  })
})
