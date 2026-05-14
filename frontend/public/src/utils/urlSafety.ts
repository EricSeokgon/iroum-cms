// SPEC-CMS-PUBLIC-001 T-007 — 외부 URL 안전성 검증 (C-02)
// http:// 또는 https:// 스킴만 허용. javascript:, data:, file:, vbscript: 등은 차단.

// @MX:ANCHOR: [AUTO] isSafeUrl — PolicyDetailView 및 외부 링크 렌더 공통 사용
// @MX:REASON: fan_in 예상 3+ (PolicyDetailView, 향후 외부 링크 컴포넌트 재사용)
// @MX:SPEC: SPEC-CMS-PUBLIC-001 §C-02
export function isSafeUrl(url: string | undefined | null): boolean {
  if (!url) return false
  const trimmed = url.trim()
  if (trimmed.length === 0) return false
  // 명시적으로 http:// 또는 https:// 로 시작해야 안전 URL 로 인정
  // URL 파서를 거치면 javascript: 가 스킴으로 인식될 수 있으나 시작 문자열 검사로 차단
  const lower = trimmed.toLowerCase()
  if (lower.startsWith('http://') || lower.startsWith('https://')) {
    // URL 파서로 추가 검증 — 잘못된 형식이면 false
    try {
      const parsed = new URL(trimmed)
      return parsed.protocol === 'http:' || parsed.protocol === 'https:'
    } catch {
      return false
    }
  }
  return false
}

// URL 에서 호스트만 추출 (표시용). 안전하지 않은 URL 이면 빈 문자열 반환.
export function extractDomain(url: string | undefined | null): string {
  if (!isSafeUrl(url)) return ''
  try {
    return new URL((url as string).trim()).hostname
  } catch {
    return ''
  }
}
