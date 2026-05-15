/**
 * useSafeHtml 단위 테스트
 *
 * SUG-2: target="_blank" 링크에 rel="noopener noreferrer" 강제 검증
 * SPEC-CMS-003 §3.1 — 관리자 본문 렌더링 XSS 방어
 */
import { describe, it, expect } from 'vitest'
import { useSafeHtml } from '@/composables/useSafeHtml'

describe('useSafeHtml', () => {
  const { sanitize } = useSafeHtml()

  // ──────────────────────────────────────────────────────
  // SUG-2: reverse tabnapping 방어
  // ──────────────────────────────────────────────────────

  it('target="_blank" 앵커에 rel="noopener noreferrer" 를 강제 삽입한다', () => {
    const input = '<a href="https://example.com" target="_blank">외부 링크</a>'
    const result = sanitize(input)

    expect(result).toContain('rel="noopener noreferrer"')
  })

  it('target="_blank" 가 없는 앵커에는 rel 을 강제하지 않는다', () => {
    const input = '<a href="/internal">내부 링크</a>'
    const result = sanitize(input)

    // rel 속성이 없거나 noopener가 강제 삽입되지 않아야 한다
    expect(result).not.toContain('noopener')
  })

  it('target="_blank" 에 이미 rel 이 있어도 noopener noreferrer 로 덮어쓴다', () => {
    const input = '<a href="https://example.com" target="_blank" rel="nofollow">링크</a>'
    const result = sanitize(input)

    expect(result).toContain('rel="noopener noreferrer"')
    expect(result).not.toContain('rel="nofollow"')
  })

  // ──────────────────────────────────────────────────────
  // 기본 XSS 방어
  // ──────────────────────────────────────────────────────

  it('script 태그를 제거한다', () => {
    const input = '<p>내용</p><script>alert("xss")</script>'
    const result = sanitize(input)

    expect(result).not.toContain('<script>')
    expect(result).toContain('<p>내용</p>')
  })

  it('onclick 등 이벤트 핸들러 속성을 제거한다', () => {
    const input = '<p onclick="alert(1)">클릭</p>'
    const result = sanitize(input)

    expect(result).not.toContain('onclick')
    expect(result).toContain('<p>')
  })

  it('null/undefined 입력 시 빈 문자열을 반환한다', () => {
    expect(sanitize(null)).toBe('')
    expect(sanitize(undefined)).toBe('')
  })

  it('허용된 태그(p, strong, em 등)는 보존한다', () => {
    const input = '<p><strong>굵게</strong> <em>기울임</em></p>'
    const result = sanitize(input)

    expect(result).toContain('<p>')
    expect(result).toContain('<strong>')
    expect(result).toContain('<em>')
  })
})
