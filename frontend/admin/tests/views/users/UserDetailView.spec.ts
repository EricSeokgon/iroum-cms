// UserDetailView 권한 변경 이력 섹션 테스트 (REQ-AUTH-016)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { createRouter, createWebHistory } from 'vue-router'
import UserDetailView from '../../../src/views/users/UserDetailView.vue'
import { usersApi } from '../../../src/api/users'
import { auditApi } from '../../../src/api/audit'
import type { UserDetail, PageResponse, PermissionChangeEntry } from '@iroum/shared/types/api'

// API mock
vi.mock('../../../src/api/users', () => ({
  usersApi: {
    detail: vi.fn(),
    unlock: vi.fn(),
    forceLogout: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock('../../../src/api/audit', () => ({
  auditApi: {
    permissionChangesByUser: vi.fn(),
  },
}))

vi.mock('../../../src/views/users/UserFormView.vue', () => ({
  default: {
    name: 'UserFormView',
    template: '<div data-testid="user-form-mock" />',
    props: ['mode', 'user'],
    emits: ['close', 'saved'],
  },
}))

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      common: { back: '뒤로', cancel: '취소', error: { unknown: '오류' } },
      users: {
        detail: {
          title: '사용자 상세', basicInfo: '기본 정보', roles: '역할', noRoles: '없음',
          activity: '활동 정보', loginHistory: '로그인 이력', loginHistoryPlaceholder: '준비 중',
        },
        field: {
          username: '사용자명', email: '이메일', name: '이름', status: '상태',
          createdAt: '가입일', updatedAt: '수정일', lastLoginAt: '마지막 로그인',
          passwordChangedAt: '비밀번호 변경일', failCount: '실패 횟수', lockedUntil: '잠금 해제',
        },
        status: { ACTIVE: '활성', INACTIVE: '비활성', LOCKED: '잠김', DELETED: '삭제됨' },
        role: { SUPER_ADMIN: '슈퍼 관리자', DEPT_ADMIN: '부서 관리자', EDITOR: '에디터', VIEWER: '뷰어' },
        action: { edit: '수정', unlock: '잠금 해제', forceLogout: '강제 로그아웃', delete: '삭제' },
        confirm: { unlock: '해제?', forceLogout: '강제 로그아웃?', delete: '삭제?' },
        error: { notFound: '없음', unlockFailed: '오류', forceLogoutFailed: '오류', deleteFailed: '오류' },
        success: { unlocked: '해제됨', forcedLogout: '완료', deleted: '삭제됨' },
      },
      audit: {
        permissionChanges: {
          userSection: { title: '최근 권한 변경 이력', viewAllLink: '전체 이력 보기' },
          empty: '조회된 이력이 없습니다',
          field: { changedAt: '발생일시', changeType: '유형', targetResource: '리소스', severity: '심각도' },
          type: { ROLE_ASSIGN: '역할 부여', ROLE_UNASSIGN: '역할 회수', ROLE_PERMISSION_GRANT: '권한 부여', ROLE_PERMISSION_REVOKE: '권한 회수' },
          severity: { INFO: '정보', WARN: '경고', CRITICAL: '치명' },
        },
      },
      a11y: { pagination: '페이지 탐색' },
    },
  },
})

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/users/:id', name: 'user-detail', component: { template: '<div />' } },
    { path: '/audit/permission-changes', name: 'permission-change-history', component: { template: '<div />' } },
    { path: '/users', name: 'user-list', component: { template: '<div />' } },
  ],
})

function makeUser(): UserDetail {
  return {
    id: 42,
    uuid: 'uuid-42',
    username: 'jdoe',
    email: 'jdoe@example.com',
    name: 'John Doe',
    status: 'ACTIVE',
    roleCodes: ['EDITOR'],
    failCount: 0,
    passwordChangedAt: '2026-01-01T00:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-02T00:00:00Z',
  }
}

function makeAuditEntry(): PermissionChangeEntry {
  return {
    id: 1,
    changeType: 'ROLE_ASSIGN',
    targetUserId: 42,
    targetUsername: 'jdoe',
    targetRoleCode: 'EDITOR',
    targetResource: 'EDITOR',
    changedBy: 1,
    changedByUsername: 'admin',
    changedAt: '2026-04-01T10:00:00Z',
    severity: 'INFO',
  }
}

function emptyAuditPage(): PageResponse<PermissionChangeEntry> {
  return { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 }
}

describe('UserDetailView — 권한 변경 이력 섹션', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(usersApi.detail).mockResolvedValue({ data: makeUser() } as never)
  })

  it('최근 권한 변경 이력 섹션 헤더가 렌더링된다', async () => {
    vi.mocked(auditApi.permissionChangesByUser).mockResolvedValueOnce({
      data: emptyAuditPage(),
    } as never)

    const wrapper = mount(UserDetailView, {
      props: { id: '42' },
      global: {
        plugins: [i18n, createPinia(), router],
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('최근 권한 변경 이력')
  })

  it('이력이 있을 때 테이블 행이 렌더링된다', async () => {
    vi.mocked(auditApi.permissionChangesByUser).mockResolvedValueOnce({
      data: { content: [makeAuditEntry()], page: 0, size: 10, totalElements: 1, totalPages: 1 },
    } as never)

    const wrapper = mount(UserDetailView, {
      props: { id: '42' },
      global: {
        plugins: [i18n, createPinia(), router],
      },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('역할 부여')
    expect(wrapper.text()).toContain('EDITOR')
  })

  it('"전체 이력 보기" 링크에 targetUserId 쿼리가 포함된다', async () => {
    vi.mocked(auditApi.permissionChangesByUser).mockResolvedValueOnce({
      data: emptyAuditPage(),
    } as never)

    const wrapper = mount(UserDetailView, {
      props: { id: '42' },
      global: {
        plugins: [i18n, createPinia(), router],
      },
    })
    await flushPromises()

    const link = wrapper.find('a[aria-label]')
    expect(link.exists()).toBe(true)
    // href에 targetUserId=42 포함 확인
    expect(link.attributes('href')).toContain('targetUserId=42')
  })
})
