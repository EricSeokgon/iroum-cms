// SPEC-CMS-PUBLIC-001 T-010 — i18n 키 완전성 검증 (E-05)
// ko / en 메시지 키가 동일해야 한다 (fallback 누락 방지)
import { describe, it, expect } from 'vitest'
import ko from '@/locales/ko.json'
import en from '@/locales/en.json'

/** 객체를 평탄화하여 점-구분 키 집합으로 반환 */
function flattenKeys(obj: Record<string, unknown>, prefix = ''): Set<string> {
  const keys = new Set<string>()
  for (const [k, v] of Object.entries(obj)) {
    const fullKey = prefix ? `${prefix}.${k}` : k
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      flattenKeys(v as Record<string, unknown>, fullKey).forEach((nested) => keys.add(nested))
    } else {
      keys.add(fullKey)
    }
  }
  return keys
}

describe('i18n keys parity — E-05', () => {
  it('ko / en 메시지 키 집합이 완전히 동일하다', () => {
    const koKeys = flattenKeys(ko as Record<string, unknown>)
    const enKeys = flattenKeys(en as Record<string, unknown>)
    const onlyInKo = [...koKeys].filter((k) => !enKeys.has(k))
    const onlyInEn = [...enKeys].filter((k) => !koKeys.has(k))
    expect(onlyInKo, `ko 에만 있는 키: ${onlyInKo.join(', ')}`).toEqual([])
    expect(onlyInEn, `en 에만 있는 키: ${onlyInEn.join(', ')}`).toEqual([])
  })

  it('error.notFound / forbidden / serverError 섹션이 양쪽 모두 정의된다', () => {
    const koAny = ko as Record<string, Record<string, unknown>>
    const enAny = en as Record<string, Record<string, unknown>>
    expect(koAny.error.notFound).toBeDefined()
    expect(koAny.error.forbidden).toBeDefined()
    expect(koAny.error.serverError).toBeDefined()
    expect(enAny.error.notFound).toBeDefined()
    expect(enAny.error.forbidden).toBeDefined()
    expect(enAny.error.serverError).toBeDefined()
  })
})
