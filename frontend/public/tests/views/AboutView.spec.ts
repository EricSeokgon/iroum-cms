// SPEC-CMS-PUBLIC-001 §5.x — AboutView 검증
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import AboutView from '@/views/AboutView.vue'

function makeI18n() {
  return createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
}

describe('AboutView', () => {
  it('미션/비전 섹션이 렌더링된다', () => {
    const wrapper = mount(AboutView, {
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.text()).toContain(koMessages.about.mission.title)
    expect(wrapper.text()).toContain(koMessages.about.vision.title)
  })

  it('핵심 가치 4개가 렌더링된다', () => {
    const wrapper = mount(AboutView, {
      global: { plugins: [makeI18n()] },
    })
    const items = wrapper.findAll('li').filter((li) =>
      li.classes().some((c) => c.includes('rounded-lg')),
    )
    expect(items.length).toBeGreaterThanOrEqual(4)
  })

  it('부서 목록이 렌더링된다', () => {
    const wrapper = mount(AboutView, {
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.text()).toContain('웹개발팀')
    expect(wrapper.text()).toContain('IT운영팀')
  })

  it('연락처 섹션이 렌더링된다', () => {
    const wrapper = mount(AboutView, {
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.text()).toContain('02-2120-0000')
    expect(wrapper.text()).toContain('contact@iroum.go.kr')
  })
})
