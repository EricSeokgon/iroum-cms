// SPEC-CMS-PUBLIC-001 T-010 — EmptyState (F-06) 검증
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import EmptyState from '@/components/common/EmptyState.vue'

function makeI18n() {
  return createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
}

describe('EmptyState — F-06', () => {
  it('role="status" 속성을 가진다 (스크린리더 친화)', () => {
    const wrapper = mount(EmptyState, { global: { plugins: [makeI18n()] } })
    const root = wrapper.find('[data-testid="empty-state"]')
    expect(root.exists()).toBe(true)
    expect(root.attributes('role')).toBe('status')
  })

  it('message prop 미지정 시 기본 메시지가 표시된다', () => {
    const wrapper = mount(EmptyState, { global: { plugins: [makeI18n()] } })
    expect(wrapper.text()).toContain('검색 결과가 없습니다')
  })
})
