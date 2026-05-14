// SPEC-CMS-PUBLIC-001 T-006 — PaginationBar 컴포넌트 검증
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createI18n } from 'vue-i18n'
import { axe } from 'jest-axe'

import PaginationBar from '@/components/common/PaginationBar.vue'
import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'

function makeI18n() {
  return createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
}

describe('PaginationBar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('totalPages > 1 일 때 nav aria-label="페이지네이션" 렌더링', () => {
    const wrapper = mount(PaginationBar, {
      props: { page: 0, pageSize: 20, totalElements: 30, totalPages: 2 },
      global: { plugins: [makeI18n()] },
    })
    const nav = wrapper.find('nav[aria-label="페이지네이션"]')
    expect(nav.exists()).toBe(true)
  })

  it('totalPages <= 1 이면 nav 미렌더링', () => {
    const wrapper = mount(PaginationBar, {
      props: { page: 0, pageSize: 20, totalElements: 5, totalPages: 1 },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('nav').exists()).toBe(false)
  })

  it('jest-axe — 접근성 위반 0건', async () => {
    const wrapper = mount(PaginationBar, {
      props: { page: 0, pageSize: 20, totalElements: 50, totalPages: 3 },
      global: { plugins: [makeI18n()] },
      attachTo: document.body,
    })
    const results = await axe(wrapper.element as HTMLElement)
    expect(results).toHaveNoViolations()
    wrapper.unmount()
  })
})
