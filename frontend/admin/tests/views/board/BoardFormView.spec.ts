// BoardFormView 단위 테스트
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
import { createI18n } from 'vue-i18n'

vi.mock('@/api/board', () => ({
  boardApi: {
    create: vi.fn(),
    update: vi.fn(),
    getMaster: vi.fn().mockResolvedValue({ data: null }),
  },
}))

import BoardFormView from '@/views/board/BoardFormView.vue'

const i18n = createI18n({
  legacy: false,
  locale: 'ko',
  fallbackLocale: 'en',
  messages: {
    ko: {
      board: {
        masters: {
          add: '게시판 추가',
          edit: '게시판 수정',
          field: {
            code: '코드',
            codePlaceholder: '게시판 코드',
            name: '이름',
            description: '설명',
            type: '유형',
          },
          error: {
            codeRequired: '코드 필수',
            nameRequired: '이름 필수',
          },
        },
      },
      common: { cancel: '취소', save: '저장' },
    },
    en: {},
  },
})

function mountView(props: Record<string, unknown> = { mode: 'create' }) {
  return mount(BoardFormView, {
    props,
    global: { plugins: [i18n, createTestingPinia({ createSpy: vi.fn })] },
  })
}

describe('BoardFormView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('create 모드에서 마운트된다', async () => {
    const wrapper = mountView({ mode: 'create' })
    await flushPromises()
    expect(wrapper.exists()).toBe(true)
  })

  it('create 모드에서 코드 입력 필드가 활성화된다', async () => {
    const wrapper = mountView({ mode: 'create' })
    await flushPromises()
    const codeInput = wrapper.find('#form-bbs-code')
    expect(codeInput.exists()).toBe(true)
    expect((codeInput.element as HTMLInputElement).disabled).toBe(false)
  })

  it('이름 입력 필드를 노출한다', async () => {
    const wrapper = mountView({ mode: 'create' })
    await flushPromises()
    const nameInput = wrapper.find('#form-bbs-name')
    expect(nameInput.exists()).toBe(true)
  })

  it('설명 textarea를 노출한다', async () => {
    const wrapper = mountView({ mode: 'create' })
    await flushPromises()
    const descInput = wrapper.find('#form-bbs-description')
    expect(descInput.exists()).toBe(true)
  })
})
