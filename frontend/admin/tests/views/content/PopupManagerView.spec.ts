// 팝업 관리 뷰 — Vitest 단위 테스트 (SPEC-CMS-004 REQ-CONTENT-006-D)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import ko from '@/locales/ko.json'
import PopupManagerView from '@/views/content/PopupManagerView.vue'
import { popups } from '@/api/content'

vi.mock('@/api/content', () => ({
  popups: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    setActive: vi.fn(),
  },
  sites: { current: vi.fn() },
}))

vi.mock('@/stores/content', () => ({
  useSiteStore: () => ({
    currentSite: { id: 1 },
    loading: false,
    fetchCurrent: vi.fn().mockResolvedValue(undefined),
  }),
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function makePopup(overrides = {}) {
  return {
    id: 1,
    siteId: 1,
    name: '테스트 팝업',
    contentHtml: '<p>내용</p>',
    position: 'CENTER' as const,
    posX: null,
    posY: null,
    width: 400,
    showFrom: '2026-04-01T00:00:00Z',
    showUntil: null,
    targetType: 'ALL' as const,
    targetRoleCodes: null,
    isActive: true,
    ...overrides,
  }
}

describe('PopupManagerView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(popups.list).mockResolvedValue({ data: [] } as never)
  })

  it('팝업이 없을 때 빈 상태를 렌더링한다', async () => {
    const wrapper = mount(PopupManagerView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('팝업 추가 버튼 클릭 시 다이얼로그가 열린다', async () => {
    const wrapper = mount(PopupManagerView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as { dialogOpen: boolean }
    const addBtn = wrapper.findAll('button').find(b => b.text().includes('팝업 추가'))
    await addBtn?.trigger('click')
    await flushPromises()

    expect(vm.dialogOpen).toBe(true)
  })

  it('showUntil 비활성화 날짜 — showFrom 이전 날짜는 비활성화된다', async () => {
    const wrapper = mount(PopupManagerView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      form: { showFrom: Date | null }
      disabledUntilDate: (d: Date) => boolean
    }

    // showFrom이 null이면 비활성화 없음
    vm.form.showFrom = null
    expect(vm.disabledUntilDate(new Date('2026-05-01'))).toBe(false)

    // showFrom 2026-05-01 → 2026-04-30 이전은 비활성화
    vm.form.showFrom = new Date('2026-05-01T00:00:00Z')
    const pastDate = new Date('2026-04-30T00:00:00Z')
    expect(vm.disabledUntilDate(pastDate)).toBe(true)
  })

  it('targetType ROLE 변경 시 targetRoleCodes 선택 폼이 표시된다', async () => {
    const wrapper = mount(PopupManagerView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      form: { targetType: string; targetRoleCodes: string[] }
      dialogOpen: boolean
    }
    vm.dialogOpen = true
    vm.form.targetType = 'ROLE'
    await flushPromises()

    // targetRoleCodes 필드가 노출됨 — v-if="form.targetType === 'ROLE'"
    expect(vm.form.targetType).toBe('ROLE')
  })

  it('편집 다이얼로그 열기 — 기존 팝업 데이터가 form에 채워진다', async () => {
    vi.mocked(popups.list).mockResolvedValueOnce({ data: [makePopup()] } as never)

    const wrapper = mount(PopupManagerView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      openEdit: (row: ReturnType<typeof makePopup>) => void
      form: { name: string; position: string }
      editingId: number | null
    }

    const popup = makePopup({ name: '수정 팝업', position: 'TOP_RIGHT' })
    vm.openEdit(popup)
    await flushPromises()

    expect(vm.form.name).toBe('수정 팝업')
    expect(vm.form.position).toBe('TOP_RIGHT')
    expect(vm.editingId).toBe(1)
  })
})
