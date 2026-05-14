// SPEC-CMS-PUBLIC-001 T-008 — SearchFilterTabs 검증 (D-01)
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

import SearchFilterTabs from '@/components/search/SearchFilterTabs.vue'
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

describe('SearchFilterTabs — D-01 6 탭', () => {
  it('6 개의 탭(ALL/POST/FAQ/QNA/POLICY/SAFETY)을 렌더링한다', () => {
    const wrapper = mount(SearchFilterTabs, {
      props: { modelValue: 'ALL', facets: {}, totalCount: 0 },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('[data-testid="search-tab-ALL"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-POST"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-FAQ"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-QNA"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-POLICY"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="search-tab-SAFETY"]').exists()).toBe(true)
  })

  it('현재 선택된 탭은 aria-selected=true', () => {
    const wrapper = mount(SearchFilterTabs, {
      props: { modelValue: 'POLICY', facets: {}, totalCount: 0 },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('[data-testid="search-tab-POLICY"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[data-testid="search-tab-ALL"]').attributes('aria-selected')).toBe('false')
  })

  it('facets 카운트가 뱃지로 표시된다', () => {
    const wrapper = mount(SearchFilterTabs, {
      props: {
        modelValue: 'ALL',
        facets: { POST: 10, FAQ: 3, POLICY: 5, SAFETY: 2, QNA: 1 },
        totalCount: 21,
      },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('[data-testid="search-tab-count-ALL"]').text()).toBe('21')
    expect(wrapper.find('[data-testid="search-tab-count-POST"]').text()).toBe('10')
    expect(wrapper.find('[data-testid="search-tab-count-POLICY"]').text()).toBe('5')
  })

  it('탭 클릭 시 update:modelValue 이벤트 emit', async () => {
    const wrapper = mount(SearchFilterTabs, {
      props: { modelValue: 'ALL', facets: {}, totalCount: 0 },
      global: { plugins: [makeI18n()] },
    })
    await wrapper.find('[data-testid="search-tab-SAFETY"]').trigger('click')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')![0]).toEqual(['SAFETY'])
  })

  it('role=tablist + role=tab 구조', () => {
    const wrapper = mount(SearchFilterTabs, {
      props: { modelValue: 'ALL', facets: {}, totalCount: 0 },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.find('[role="tablist"]').exists()).toBe(true)
    const tabs = wrapper.findAll('[role="tab"]')
    expect(tabs.length).toBe(6)
  })
})
