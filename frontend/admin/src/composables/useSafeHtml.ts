/**
 * useSafeHtml — Admin SPA v-html XSS 방어용 sanitize 컴포저블
 *
 * 백엔드 OWASP HTML Sanitizer 의 1차 방어를 신뢰하지 않고 클라이언트에서 2차 정화 수행.
 * 게시판/Q&A/설문/안전관리/검색 등 v-html 사용 위치 전체에서 공통 사용.
 *
 * @MX:ANCHOR: [AUTO] sanitize — XSS 방어 화이트리스트 기반 정화
 * @MX:REASON: PostDetailView, PublicationDetailView, QnaDetailView, SurveyDetailView,
 *            TemplateManageView, GuidelineReportView, SearchView 등 7곳 이상에서 사용
 * @MX:SPEC: SPEC-CMS-003 §3.1 (관리자 본문 렌더링 XSS 방어)
 */
import DOMPurify from 'dompurify'

// 허용 태그/속성 — 게시판/리포트/검색 본문 렌더링용
// public SPA NoticeContent 패턴과 동일한 화이트리스트 유지
const ALLOWED_TAGS = [
  'p',
  'br',
  'strong',
  'em',
  'b',
  'i',
  'u',
  's',
  'ul',
  'ol',
  'li',
  'a',
  'img',
  'table',
  'thead',
  'tbody',
  'tr',
  'td',
  'th',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'blockquote',
  'pre',
  'code',
  'span',
  'div',
  'figure',
  'figcaption',
  // 검색 결과 하이라이트
  'mark',
]

const ALLOWED_ATTR = [
  'href',
  'src',
  'alt',
  'class',
  'style',
  'title',
  'target',
  'rel',
]

const FORBID_TAGS = ['script', 'iframe', 'object', 'embed', 'form']
const FORBID_ATTR = ['onerror', 'onload', 'onclick', 'onmouseover', 'onfocus', 'onblur']

// SUG-2: target="_blank" 링크에 rel="noopener noreferrer" 강제 — reverse tabnapping 방어
// 모듈 로드 시 1회 등록. DOMPurify hook 은 전역 누적되지 않도록 addHook 은 최상위에서만 호출.
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node instanceof HTMLAnchorElement && node.getAttribute('target') === '_blank') {
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

export function useSafeHtml() {
  function sanitize(html: string | null | undefined): string {
    if (!html) return ''
    return DOMPurify.sanitize(html, {
      ALLOWED_TAGS,
      ALLOWED_ATTR,
      FORBID_TAGS,
      FORBID_ATTR,
      ALLOW_DATA_ATTR: false,
    }) as string
  }

  return { sanitize }
}
