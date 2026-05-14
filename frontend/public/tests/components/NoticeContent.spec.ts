// SPEC-CMS-PUBLIC-001 T-006 — NoticeContent sanitize 검증 (B-04)
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

import NoticeContent from '@/components/notice/NoticeContent.vue'

describe('NoticeContent — B-04 sanitize 검증', () => {
  it('script 태그를 제거한다', () => {
    const html = '<p>안녕</p><script>alert("xss")</script>'
    const wrapper = mount(NoticeContent, { props: { html } })
    const root = wrapper.find('[data-testid="notice-content"]')
    expect(root.html()).toContain('안녕')
    expect(root.html()).not.toContain('<script>')
    expect(root.html()).not.toContain('alert')
  })

  it('onerror 속성을 제거한다', () => {
    const html = '<img src="x" onerror="alert(1)" alt="test" />'
    const wrapper = mount(NoticeContent, { props: { html } })
    const root = wrapper.find('[data-testid="notice-content"]')
    expect(root.html()).not.toContain('onerror')
  })

  it('iframe 태그를 제거한다', () => {
    const html = '<iframe src="evil.com"></iframe><p>본문</p>'
    const wrapper = mount(NoticeContent, { props: { html } })
    const root = wrapper.find('[data-testid="notice-content"]')
    expect(root.html()).not.toContain('<iframe')
    expect(root.html()).toContain('본문')
  })

  it('허용된 태그(p, strong, ul, li, a, table 등)를 유지한다', () => {
    const html =
      '<p>본문</p><strong>강조</strong><ul><li>항목</li></ul><a href="https://example.com">링크</a>'
    const wrapper = mount(NoticeContent, { props: { html } })
    const root = wrapper.find('[data-testid="notice-content"]')
    expect(root.html()).toContain('<p>본문</p>')
    expect(root.html()).toContain('<strong>강조</strong>')
    expect(root.html()).toContain('<ul>')
    expect(root.html()).toContain('<a href="https://example.com"')
  })

  it('빈 html 입력에도 에러 없이 렌더링', () => {
    const wrapper = mount(NoticeContent, { props: { html: '' } })
    expect(wrapper.find('[data-testid="notice-content"]').exists()).toBe(true)
  })

  it('렌더 결과 DOM 에 script 노드가 존재하지 않는다 (B-04 evidence)', () => {
    const html = '<p>안전</p><script>alert(1)</script><img onerror="x" src="y" />'
    const wrapper = mount(NoticeContent, { props: { html }, attachTo: document.body })
    const scripts = (wrapper.element as HTMLElement).querySelectorAll('script')
    expect(scripts.length).toBe(0)
    wrapper.unmount()
  })
})
