// CodeManagerView 단위 테스트 — SPEC-CMS-005 Bundle D REQ-SYS-003-D
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import CodeManagerView from '@/views/system/CodeManagerView.vue'
import { codeGroups, codes } from '@/api/system'
import type { CodeGroupResponse, CodeResponse } from '@/api/system'

vi.mock('@/api/system', () => ({
  codeGroups: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
  codes: {
    list: vi.fn(),
    bulk: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
  dashboard: { kpi: vi.fn(), trends: vi.fn(), topPages: vi.fn() },
  settings: {},
  maintenance: {},
  auditLogs: {},
  accessLogs: {},
  stats: {},
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

const sampleGroup: CodeGroupResponse = {
  code: 'STATUS',
  name: '상태 코드',
  sort_order: 0,
  status: 'ACTIVE',
  code_count: 2,
}

const sampleCodes: CodeResponse[] = [
  { id: 1, group_code: 'STATUS', code: 'ACTIVE', name: '활성', sort_order: 0, status: 'ACTIVE' },
  { id: 2, group_code: 'STATUS', code: 'INACTIVE', name: '비활성', sort_order: 1, status: 'ACTIVE' },
]

describe('CodeManagerView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(codeGroups.list).mockResolvedValue({ data: [sampleGroup] } as never)
    vi.mocked(codes.list).mockResolvedValue({ data: sampleCodes } as never)
  })

  it('마운트 시 그룹 목록을 로드한다', async () => {
    mount(CodeManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()
    expect(codeGroups.list).toHaveBeenCalledOnce()
  })

  it('그룹 선택 시 해당 코드 목록을 조회한다', async () => {
    const wrapper = mount(CodeManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      onGroupSelect: (row: CodeGroupResponse) => Promise<void>
    }
    await vm.onGroupSelect(sampleGroup)
    await flushPromises()

    expect(codes.list).toHaveBeenCalledWith('STATUS')
  })

  it('그룹 생성 폼에 code/name 필수 검증이 있다', () => {
    const wrapper = mount(CodeManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })

    const vm = wrapper.vm as {
      groupRules: { code: Array<{ required: boolean }>; name: Array<{ required: boolean }> }
    }
    expect(vm.groupRules.code.some(r => r.required)).toBe(true)
    expect(vm.groupRules.name.some(r => r.required)).toBe(true)
  })

  it('그룹 편집 시 기존 데이터가 폼에 채워진다', async () => {
    const wrapper = mount(CodeManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      openGroupEdit: (row: CodeGroupResponse) => void
      groupForm: { code: string; name: string }
    }
    vm.openGroupEdit(sampleGroup)

    expect(vm.groupForm.code).toBe('STATUS')
    expect(vm.groupForm.name).toBe('상태 코드')
  })

  it('벌크 테스트 결과가 표시된다', async () => {
    vi.mocked(codes.bulk).mockResolvedValue({
      data: { STATUS: sampleCodes },
    } as never)

    const wrapper = mount(CodeManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      bulkInput: string
      runBulkTest: () => Promise<void>
      bulkResult: Record<string, CodeResponse[]> | null
    }
    vm.bulkInput = 'STATUS'
    await vm.runBulkTest()
    await flushPromises()

    expect(vm.bulkResult).toBeDefined()
    expect(vm.bulkResult?.STATUS).toHaveLength(2)
  })
})
