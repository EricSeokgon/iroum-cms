// MaintenanceManagerView 단위 테스트 — SPEC-CMS-005 Bundle D REQ-SYS-005-D
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ko from '@/locales/ko.json'
import MaintenanceManagerView from '@/views/system/MaintenanceManagerView.vue'
import { maintenance } from '@/api/system'
import type { MaintenanceResponse } from '@/api/system'

vi.mock('@/api/system', () => ({
  maintenance: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    activate: vi.fn(),
    cancel: vi.fn(),
  },
  dashboard: { kpi: vi.fn(), trends: vi.fn(), topPages: vi.fn() },
  codeGroups: {},
  codes: {},
  settings: {},
  auditLogs: {},
  accessLogs: {},
  stats: {},
}))

// ElMessageBox 모킹
vi.mock('element-plus', async () => {
  const actual = await vi.importActual<typeof import('element-plus')>('element-plus')
  return {
    ...actual,
    ElMessageBox: {
      confirm: vi.fn().mockResolvedValue('confirm'),
    },
    ElMessage: {
      success: vi.fn(),
      error: vi.fn(),
    },
  }
})

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

const scheduledMaintenance: MaintenanceResponse = {
  id: 1,
  start_at: '2026-05-01T00:00:00',
  end_at: '2026-05-01T06:00:00',
  status: 'SCHEDULED',
  message_ko: '정기 점검입니다',
  message_en: 'Scheduled maintenance',
  allow_admin_access: true,
  created_at: '2026-04-30T10:00:00',
}

const activeMaintenance: MaintenanceResponse = {
  ...scheduledMaintenance,
  id: 2,
  status: 'ACTIVE',
}

describe('MaintenanceManagerView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(maintenance.list).mockResolvedValue({ data: [scheduledMaintenance] } as never)
  })

  it('마운트 시 점검 목록이 로드된다', async () => {
    mount(MaintenanceManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()
    expect(maintenance.list).toHaveBeenCalledOnce()
  })

  it('start_at < end_at 검증이 있다', () => {
    const wrapper = mount(MaintenanceManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    const vm = wrapper.vm as {
      rules: { dateRange: Array<{ validator: (rule: unknown, val: [string, string], cb: (e?: Error) => void) => void }> }
    }

    // start >= end 케이스 → 에러
    const validator = vm.rules.dateRange[0].validator
    const cbError = vi.fn()
    validator(null, ['2026-05-01T06:00:00', '2026-05-01T00:00:00'], cbError)
    expect(cbError).toHaveBeenCalledWith(expect.any(Error))

    // start < end 케이스 → 통과
    const cbOk = vi.fn()
    validator(null, ['2026-05-01T00:00:00', '2026-05-01T06:00:00'], cbOk)
    expect(cbOk).toHaveBeenCalledWith()
  })

  it('allow_admin_access 기본값은 true이다', () => {
    const wrapper = mount(MaintenanceManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    const vm = wrapper.vm as { form: { allow_admin_access: boolean } }
    expect(vm.form.allow_admin_access).toBe(true)
  })

  it('ACTIVE 점검이 있으면 활성 배너가 표시된다', async () => {
    vi.mocked(maintenance.list).mockResolvedValue({ data: [activeMaintenance] } as never)

    const wrapper = mount(MaintenanceManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    // [role="alert"] 배너 존재 확인
    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
  })

  it('편집 다이얼로그 열기 — 기존 데이터가 폼에 채워진다', async () => {
    const wrapper = mount(MaintenanceManagerView, {
      global: { plugins: [i18n, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      openEdit: (row: MaintenanceResponse) => void
      form: { message_ko: string; allow_admin_access: boolean }
    }
    vm.openEdit(scheduledMaintenance)

    expect(vm.form.message_ko).toBe('정기 점검입니다')
    expect(vm.form.allow_admin_access).toBe(true)
  })
})
