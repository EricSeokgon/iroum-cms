/**
 * RoleMatrixView 단위 테스트 — REQ-AUTH-013
 * 역할 목록 렌더링, 역할 선택 → 매트릭스 로드, 권한 저장, 시스템 역할 readonly를 검증합니다
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import RoleMatrixView from '../../../src/views/roles/RoleMatrixView.vue'
import type { RoleSummary, RoleDetail, PermissionSummary } from '@iroum/shared/types/api'

// rolesApi / permissionsApi 모킹
vi.mock('../../../src/api/roles', () => ({
  rolesApi: {
    list: vi.fn(),
    detail: vi.fn(),
    delete: vi.fn(),
    updatePermissions: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
  },
  permissionsApi: {
    list: vi.fn(),
  },
}))

import { rolesApi, permissionsApi } from '../../../src/api/roles'

const mockList = vi.mocked(rolesApi.list)
const mockDetail = vi.mocked(rolesApi.detail)
const mockUpdatePerms = vi.mocked(rolesApi.updatePermissions)
const mockPermList = vi.mocked(permissionsApi.list)

const ROLES: RoleSummary[] = [
  {
    code: 'SUPER_ADMIN',
    name: '슈퍼 관리자',
    isSystem: true,
    aliasedTo: null,
    userCount: 1,
    permissionCount: 15,
    createdAt: '2026-01-01T00:00:00Z',
  },
  {
    code: 'EDITOR_PLUS',
    name: '에디터 플러스',
    isSystem: false,
    aliasedTo: null,
    userCount: 3,
    permissionCount: 2,
    createdAt: '2026-01-02T00:00:00Z',
  },
]

const PERMS: PermissionSummary[] = [
  { code: 'USER:READ', resource: 'USER', action: 'READ' },
  { code: 'ROLE:READ', resource: 'ROLE', action: 'READ' },
]

const SUPER_DETAIL: RoleDetail = {
  code: 'SUPER_ADMIN',
  name: '슈퍼 관리자',
  isSystem: true,
  aliasedTo: null,
  userCount: 1,
  permissionCodes: ['USER:READ', 'ROLE:READ'],
  createdAt: '2026-01-01T00:00:00Z',
}

const EDITOR_DETAIL: RoleDetail = {
  code: 'EDITOR_PLUS',
  name: '에디터 플러스',
  isSystem: false,
  aliasedTo: null,
  userCount: 3,
  permissionCodes: ['USER:READ'],
  createdAt: '2026-01-02T00:00:00Z',
}

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      roles: {
        list: '역할 목록',
        detail: '권한 매트릭스',
        field: { code: '코드', name: '이름', isSystem: '시스템 역할', userCount: '사용자 수' },
        action: { add: '역할 추가', edit: '편집', delete: '삭제', save: '변경 사항 저장', reset: '초기화' },
        matrix: { title: '{role} 권한 매트릭스', noChanges: '변경 없음', saved: '저장됨', selectRoleHint: '역할을 선택하세요', systemRoleReadonly: '읽기 전용' },
        form: { createTitle: '새 역할', editTitle: '편집: {code}', codeHint: '코드 힌트' },
        error: { notFound: '역할 없음', systemRoleProtected: '시스템 역할 보호', hasUsers: '{count}명 있음', duplicateCode: '중복', invalidCode: '잘못된 코드' },
        success: { created: '생성', updated: '수정', deleted: '삭제', permissionsUpdated: '권한 저장됨' },
        alias: '{aliasedTo} 별칭',
      },
      permissions: {
        title: '권한',
        field: { resource: '리소스', action: '액션' },
        resource: { USER: '사용자', ROLE: '역할' },
        action: { READ: '조회', WRITE: '수정', DELETE: '삭제', EXECUTE: '실행', ADMIN: '관리' },
      },
      users: { col: { actions: '액션' } },
      organizations: { confirm: { delete: '{name} 삭제?' } },
      common: { cancel: '취소', loading: '로딩 중...', error: { unknown: '오류' } },
      a11y: { permissionCell: '{resource} {action}', permissionNotAvailable: '없음' },
    },
  },
})

function mountView() {
  return mount(RoleMatrixView, {
    global: {
      plugins: [i18n, ElementPlus],
      stubs: { RoleFormView: true, teleport: true },
    },
  })
}

describe('RoleMatrixView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockList.mockResolvedValue({ data: ROLES } as any)
    mockPermList.mockResolvedValue({ data: PERMS } as any)
    mockDetail.mockImplementation(async (code: string) => ({
      data: code === 'SUPER_ADMIN' ? SUPER_DETAIL : EDITOR_DETAIL,
    }))
    mockUpdatePerms.mockResolvedValue({ data: undefined } as any)
  })

  it('onMounted에서 역할 목록을 API에서 가져와 렌더링한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(mockList).toHaveBeenCalledOnce()
    expect(wrapper.text()).toContain('SUPER_ADMIN')
    expect(wrapper.text()).toContain('EDITOR_PLUS')
  })

  it('역할 선택 시 detail API를 호출하고 권한 코드를 로드한다', async () => {
    const wrapper = mountView()
    await flushPromises()
    // 테이블 행 클릭 시뮬레이션은 el-table의 current-change 이벤트를 직접 emit
    const table = wrapper.findComponent({ name: 'ElTable' })
    await table.vm.$emit('current-change', ROLES[1])
    await flushPromises()
    expect(mockDetail).toHaveBeenCalledWith('EDITOR_PLUS')
  })

  it('권한 변경 후 저장 버튼 클릭 시 updatePermissions API가 호출된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    // EDITOR_PLUS 선택
    const table = wrapper.findComponent({ name: 'ElTable' })
    await table.vm.$emit('current-change', ROLES[1])
    await flushPromises()
    // PermissionMatrixGrid의 update:modelValue 이벤트로 권한 변경 시뮬레이션
    const grid = wrapper.findComponent({ name: 'PermissionMatrixGrid' })
    await grid.vm.$emit('update:modelValue', ['USER:READ', 'ROLE:READ'])
    await flushPromises()
    // 저장 버튼 클릭
    const saveBtn = wrapper.findAll('button').find((b) => b.text().includes('변경 사항 저장'))
    await saveBtn?.trigger('click')
    await flushPromises()
    expect(mockUpdatePerms).toHaveBeenCalledWith('EDITOR_PLUS', ['USER:READ', 'ROLE:READ'])
  })

  it('시스템 역할 선택 시 매트릭스가 readonly로 렌더링된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    const table = wrapper.findComponent({ name: 'ElTable' })
    await table.vm.$emit('current-change', ROLES[0])
    await flushPromises()
    // 시스템 역할 뱃지 표시
    expect(wrapper.text()).toContain('시스템 역할')
    // 저장 버튼이 없어야 함 (isSystem = true)
    const saveBtn = wrapper.findAll('button').find((b) => b.text().includes('변경 사항 저장'))
    expect(saveBtn).toBeUndefined()
  })

  it('역할 미선택 시 selectRoleHint 안내 메시지가 표시된다', async () => {
    const wrapper = mountView()
    await flushPromises()
    expect(wrapper.text()).toContain('역할을 선택하세요')
  })
})
