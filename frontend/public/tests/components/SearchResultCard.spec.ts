// SPEC-CMS-PUBLIC-001 T-008 — SearchResultCard 검증 (D-02 mark 하이라이트)
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import { createI18n } from 'vue-i18n'

import SearchResultCard from '@/components/search/SearchResultCard.vue'
import koMessages from '@/locales/ko.json'
import enMessages from '@/locales/en.json'
import type { SearchResultItem } from '@/api/searchApi'

function makeI18n() {
  return createI18n({
    legacy: false,
    locale: 'ko',
    fallbackLocale: 'en',
    messages: { ko: koMessages, en: enMessages },
  })
}

const sampleItem: SearchResultItem = {
  docType: 'POST',
  docId: 1,
  title: '안전 가이드 안내',
  snippet: '안전 <mark>가이드</mark>를 확인하세요',
  highlight: '',
  rank: 1,
  domain: 'board',
  url: '/notices/1',
  createdAt: '2026-04-15T09:00:00Z',
}

describe('SearchResultCard — D-02 mark 하이라이트', () => {
  it('제목, 타입 뱃지, snippet 을 렌더링한다', () => {
    const wrapper = mount(SearchResultCard, {
      props: { item: sampleItem },
      global: { plugins: [makeI18n()] },
    })
    expect(wrapper.text()).toContain('안전 가이드 안내')
    expect(wrapper.find('[data-testid="search-type-badge-POST"]').exists()).toBe(true)
  })

  it('snippet 의 <mark> 태그가 보존된다', () => {
    const wrapper = mount(SearchResultCard, {
      props: { item: sampleItem },
      global: { plugins: [makeI18n()] },
    })
    const snippet = wrapper.find('[data-testid="search-result-snippet"]')
    expect(snippet.html()).toContain('<mark>가이드</mark>')
  })

  it('snippet 의 <script> 태그는 제거된다 (XSS 방어)', () => {
    const wrapper = mount(SearchResultCard, {
      props: {
        item: {
          ...sampleItem,
          snippet: '안전 <script>alert(1)</script>가이드',
        },
      },
      global: { plugins: [makeI18n()] },
    })
    const snippet = wrapper.find('[data-testid="search-result-snippet"]')
    expect(snippet.html()).not.toContain('<script>')
    // DOM 상에도 script 노드가 없어야 함
    const scripts = snippet.element.querySelectorAll('script')
    expect(scripts.length).toBe(0)
  })

  it('snippet 의 <a> 태그도 제거된다 (mark 만 허용)', () => {
    const wrapper = mount(SearchResultCard, {
      props: {
        item: {
          ...sampleItem,
          snippet: '<a href="javascript:alert(1)">위험</a> <mark>안전</mark>',
        },
      },
      global: { plugins: [makeI18n()] },
    })
    const snippet = wrapper.find('[data-testid="search-result-snippet"]')
    expect(snippet.html()).not.toContain('<a ')
    expect(snippet.html()).toContain('<mark>안전</mark>')
  })

  it('url 링크가 정상적으로 렌더링된다', () => {
    const wrapper = mount(SearchResultCard, {
      props: { item: sampleItem },
      global: { plugins: [makeI18n()] },
    })
    const link = wrapper.find('a')
    expect(link.attributes('href')).toBe('/notices/1')
  })
})
