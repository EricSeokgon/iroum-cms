// SPEC-CMS-PUBLIC-001 T-006 — FaqView 검증 (B-06 아코디언 키보드)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

const listMock = vi.fn()
const categoriesMock = vi.fn()
vi.mock('@/api/faqApi', () => ({
  faqApi: {
    list: (...args: unknown[]) => listMock(...args),
    categories: (...args: unknown[]) => categoriesMock(...args),
  },
}))

async function mountView() {
  const i18n = createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/faqs', name: 'faq', component: () => import('@/views/FaqView.vue') }],
  })
  router.push('/faqs')
  await router.isReady()
  const FaqView = (await import('@/views/FaqView.vue')).default
  const wrapper = mount(FaqView, { global: { plugins: [i18n, router] } })
  await flushPromises()
  return wrapper
}

describe('FaqView — B-06 아코디언 키보드', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listMock.mockReset()
    categoriesMock.mockReset()
    localStorage.clear()
    categoriesMock.mockResolvedValue([{ code: 'GENERAL', name: '일반' }])
    listMock.mockResolvedValue({
      content: [
        { id: 1, question: '질문 1?', answer: '답변 1', categoryCode: 'GENERAL', sortOrder: 1 },
        { id: 2, question: '질문 2?', answer: '답변 2', categoryCode: 'GENERAL', sortOrder: 2 },
      ],
      page: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
    })
  })

  it('각 FAQ 항목은 button[aria-expanded] + panel[aria-hidden] 구조', async () => {
    const wrapper = await mountView()
    const headers = wrapper.findAll('button[aria-expanded]')
    expect(headers.length).toBeGreaterThanOrEqual(2)
    expect(headers[0].attributes('aria-expanded')).toBe('false')
    expect(wrapper.find('[data-testid="faq-panel-0"]').attributes('aria-hidden')).toBe('true')
  })

  it('헤더 클릭 시 aria-expanded=true 토글 + 패널 aria-hidden=false', async () => {
    const wrapper = await mountView()
    const header = wrapper.find('[data-testid="faq-header-0"]')
    await header.trigger('click')
    expect(header.attributes('aria-expanded')).toBe('true')
    const panel = wrapper.find('[data-testid="faq-panel-0"]')
    expect(panel.attributes('aria-hidden')).toBe('false')
  })

  it('Enter 키로 아코디언 토글 (B-06)', async () => {
    const wrapper = await mountView()
    const header = wrapper.find('[data-testid="faq-header-0"]')
    await header.trigger('keydown.enter')
    expect(header.attributes('aria-expanded')).toBe('true')
  })

  it('Space 키로 아코디언 토글 (B-06)', async () => {
    const wrapper = await mountView()
    const header = wrapper.find('[data-testid="faq-header-1"]')
    await header.trigger('keydown.space')
    expect(header.attributes('aria-expanded')).toBe('true')
  })

  it('카테고리 선택 후 검색 → faqApi.list 가 categoryCode 와 함께 호출', async () => {
    const wrapper = await mountView()
    listMock.mockClear()
    await wrapper.find('[data-testid="faq-category-select"]').setValue('GENERAL')
    await wrapper.find('[data-testid="faq-keyword-input"]').setValue('가입')
    await wrapper.find('form').trigger('submit')
    await flushPromises()
    const lastCall = listMock.mock.calls[listMock.mock.calls.length - 1][0]
    expect(lastCall).toMatchObject({ categoryCode: 'GENERAL', keyword: '가입' })
  })
})
