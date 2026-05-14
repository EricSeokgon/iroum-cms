/**
 * RoleFormView 단위 테스트 — REQ-AUTH-013
 * create/edit 모드 전환, 코드 정규식 검증, 서버 에러 처리를 검증합니다
 */

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import RoleFormView from '../../../src/views/roles/RoleFormView.vue'
import type { RoleDetail, PermissionSummary } from '@iroum/shared/types/api'
import axios from 'axios'

// rolesApi 모킹
vi.mock('../../../src/api/roles', () => ({
  rolesApi: {
    create: vi.fn(),
    update: vi.fn(),
    detail: vi.fn(),
  },
  permissionsApi: {
    list: vi.fn(),
  },
}))

import { rolesApi, permissionsApi } from '../../../src/api/roles'

const mockCreate = vi.mocked(rolesApi.create)
const mockUpdate = vi.mocked(rolesApi.update)
const mockDetail = vi.mocked(rolesApi.detail)
const mockPermList = vi.mocked(permissionsApi.list)

const SAMPLE_PERMS: PermissionSummary[] = [
  { code: 'USER:READ', resource: 'USER', action: 'READ' },
  { code: 'ROLE:READ', resource: 'ROLE', action: 'READ' },
]

const SAMPLE_DETAIL: RoleDetail = {
  code: 'EDITOR_PLUS',
  name: '에디터 플러스',
  description: '테스트 역할',
  isSystem: false,
  aliasedTo: null,
  userCount: 0,
  permissionCodes: ['USER:READ'],
  createdAt: '2026-01-01T00:00:00Z',
}

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  messages: {
    ko: {
      roles: {
        form: { createTitle: '새 역할', editTitle: '역할 편집: {code}', codeHint: '대문자 + 밑줄' },
        field: { code: '코드', name: '이름', description: '설명', isSystem: '시스템 역할' },
        action: { add: '역할 추가', edit: '편집' },
        error: { duplicateCode: '중복 코드', invalidCode: '잘못된 코드', systemRoleProtected: '시스템 역할' },
        success: { created: '생성 완료', updated: '수정 완료' },
        matrix: { systemRoleReadonly: '읽기 전용' },
      },
      permissions: {
        title: '권한',
        resource: { USER: '사용자', ROLE: '역할' },
        action: { READ: '조회', WRITE: '수정', DELETE: '삭제', EXECUTE: '실행', ADMIN: '관리' },
      },
      organizations: { error: { nameRequired: '이름 필수', nameLength: '이름 길이' } },
      common: { cancel: '취소', loading: '로딩 중...', error: { unknown: '오류 발생' } },
    },
  },
})

function mountForm(props: { mode: 'create' | 'edit'; roleCode?: string; isSystem?: boolean }) {
  return mount(RoleFormView, {
    props,
    // teleport stub은 setup.ts 전역에서 비활성화됨 — el-dialog 콘텐츠가 렌더링되어야 함
    global: { plugins: [i18n, ElementPlus] },
  })
}

describe('RoleFormView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockPermList.mockResolvedValue({ data: SAMPLE_PERMS } as any)
    mockDetail.mockResolvedValue({ data: SAMPLE_DETAIL } as any)
  })

  it('create 모드에서 코드 입력 필드가 활성화된다', async () => {
    const wrapper = mountForm({ mode: 'create' })
    await flushPromises()
    const codeInput = wrapper.find('#role-form-code')
    expect(codeInput.exists()).toBe(true)
    expect((codeInput.element as HTMLInputElement).disabled).toBe(false)
  })

  it('소문자 코드 입력 시 유효성 검증이 실패한다', async () => {
    const wrapper = mountForm({ mode: 'create' })
    await flushPromises()
    const codeInput = wrapper.find('#role-form-code')
    await codeInput.setValue('lowercase_code')
    // 폼 제출 시도
    const submitBtn = wrapper.findAll('button').find((b) => b.text().includes('역할 추가'))
    await submitBtn?.trigger('click')
    await flushPromises()
    // create API가 호출되지 않아야 함
    expect(mockCreate).not.toHaveBeenCalled()
  })

  it('edit 모드에서 코드 필드가 disabled이고 기존 데이터가 채워진다', async () => {
    const wrapper = mountForm({ mode: 'edit', roleCode: 'EDITOR_PLUS', isSystem: false })
    await flushPromises()
    const codeInput = wrapper.find('#role-form-code')
    expect((codeInput.element as HTMLInputElement).disabled).toBe(true)
    const nameInput = wrapper.find('#role-form-name')
    expect((nameInput.element as HTMLInputElement).value).toBe('에디터 플러스')
  })

  it('중복 코드 에러 시 submitError 메시지가 표시된다', async () => {
    mockCreate.mockRejectedValue(
      Object.assign(new Error('duplicate'), {
        isAxiosError: true,
        response: { data: { code: 'DUPLICATE_ROLE_CODE' }, status: 409 },
      }),
    )
    // axios.isAxiosError mock
    vi.spyOn(axios, 'isAxiosError').mockReturnValue(true)

    const wrapper = mountForm({ mode: 'create' })
    await flushPromises()
    // 유효한 코드 입력
    const codeInput = wrapper.find('#role-form-code')
    await codeInput.setValue('VALID_CODE')
    const nameInput = wrapper.find('#role-form-name')
    await nameInput.setValue('Valid Name')

    const submitBtn = wrapper.findAll('button').find((b) => b.text().includes('역할 추가'))
    await submitBtn?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('중복 코드')
  })
})
