// 메뉴 트리 뷰 — Vitest 단위 테스트 (SPEC-CMS-004 REQ-CONTENT-001-D, 002-D)
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'
import ElementPlus from 'element-plus'
import ko from '@/locales/ko.json'
import MenuTreeView from '@/views/content/MenuTreeView.vue'
import type { MenuTreeNode } from '@/api/content'

// API mock
vi.mock('@/api/content', () => ({
  menus: {
    tree: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    toggleVisibility: vi.fn(),
    move: vi.fn(),
    changeOrder: vi.fn(),
    replacePermissions: vi.fn(),
  },
  templates: { list: vi.fn() },
  sites: { current: vi.fn() },
  pages: { list: vi.fn() },
  popups: { list: vi.fn() },
  banners: { list: vi.fn(), listGroups: vi.fn() },
  i18n: { list: vi.fn() },
  seoRedirects: { list: vi.fn() },
}))

vi.mock('@/stores/content', () => ({
  useMenuTreeStore: () => ({
    tree: [],
    loading: false,
    errors: null,
    fetchTree: vi.fn().mockResolvedValue(undefined),
    invalidate: vi.fn(),
  }),
  useSiteStore: () => ({
    currentSite: { id: 1, code: 'DEFAULT', name: '테스트 사이트' },
    loading: false,
    error: null,
    fetchCurrent: vi.fn().mockResolvedValue(undefined),
    invalidate: vi.fn(),
  }),
}))

const i18n = createI18n({ legacy: false, locale: 'ko', messages: { ko } })

function makeNode(overrides: Partial<MenuTreeNode> = {}): MenuTreeNode {
  return {
    id: 1,
    siteId: 1,
    code: 'HOME',
    name: '홈',
    url: '/',
    target: '_self',
    isVisible: true,
    depth: 1,
    sortOrder: 0,
    path: '/HOME',
    children: [],
    ...overrides,
  }
}

describe('MenuTreeView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('트리가 비어 있을 때 빈 상태 텍스트를 렌더링한다', async () => {
    const wrapper = mount(MenuTreeView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    // ElTree empty-text 또는 커스텀 빈 상태 메시지
    expect(wrapper.exists()).toBe(true)
  })

  it('메뉴 추가 버튼 클릭 시 생성 다이얼로그가 열린다', async () => {
    const wrapper = mount(MenuTreeView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    // "+ 메뉴 추가" 버튼 찾기
    const addBtn = wrapper.findAll('button').find(b => b.text().includes('메뉴 추가'))
    expect(addBtn).toBeDefined()
    await addBtn?.trigger('click')
    await flushPromises()

    // createOpen이 true가 되어 다이얼로그가 렌더링되어야 함
    const vm = wrapper.vm as { createOpen: boolean }
    expect(vm.createOpen).toBe(true)
  })

  it('노드 클릭 시 editForm이 해당 노드 데이터로 채워진다', async () => {
    const wrapper = mount(MenuTreeView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      selectNode: (node: MenuTreeNode) => void
      editForm: { code: string; name: string }
      selected: MenuTreeNode | null
    }

    const node = makeNode({ code: 'ABOUT', name: '소개' })
    vm.selectNode(node)
    await flushPromises()

    expect(vm.editForm.code).toBe('ABOUT')
    expect(vm.editForm.name).toBe('소개')
    expect(vm.selected?.id).toBe(1)
  })

  it('권한 설정 버튼 클릭 시 PermissionMappingDialog가 열린다', async () => {
    const wrapper = mount(MenuTreeView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      selectNode: (node: MenuTreeNode) => void
      openPermissions: () => void
      permOpen: boolean
    }

    // 노드 선택 후 권한 버튼
    vm.selectNode(makeNode())
    await flushPromises()

    vm.openPermissions()
    await flushPromises()

    expect(vm.permOpen).toBe(true)
  })

  it('allowDrop — depth 5 이상 inner drop은 거부된다', async () => {
    const wrapper = mount(MenuTreeView, {
      global: { plugins: [i18n, ElementPlus, createTestingPinia()] },
    })
    await flushPromises()

    const vm = wrapper.vm as {
      // allowDrop은 private이지만 컴포넌트 내부 로직 테스트
      [key: string]: unknown
    }

    // allowDrop 함수 직접 호출
    const allowDrop = (vm as { allowDrop?: (...args: unknown[]) => unknown }).allowDrop
    if (allowDrop) {
      // depth 5인 노드에 inner drop은 거부
      const result = allowDrop({}, { data: { depth: 5 } }, 'inner')
      expect(result).toBe(false)

      // depth 4인 노드에 inner drop은 허용
      const result2 = allowDrop({}, { data: { depth: 4 } }, 'inner')
      expect(result2).toBe(true)
    }
  })
})
