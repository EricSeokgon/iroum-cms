// Vitest 글로벌 셋업 — jest-axe 매처 확장 + DOMPurify · ResizeObserver 폴리필
// @MX:NOTE: [AUTO] Phase 0 foundation 테스트 인프라 — 모든 spec 파일에 자동 적용

import { expect, vi, beforeEach } from 'vitest'
import { toHaveNoViolations } from 'jest-axe'

// jest-axe 매처를 Vitest expect 에 확장
expect.extend(toHaveNoViolations)

// jsdom 에 없는 ResizeObserver / IntersectionObserver 폴리필 (Element Plus, echarts 등 의존)
class ResizeObserverMock {
  observe() { /* noop */ }
  unobserve() { /* noop */ }
  disconnect() { /* noop */ }
}

class IntersectionObserverMock {
  observe() { /* noop */ }
  unobserve() { /* noop */ }
  disconnect() { /* noop */ }
  takeRecords() { return [] }
  root = null
  rootMargin = ''
  thresholds = []
}

// @MX:NOTE: [AUTO] window 전역 폴리필 — jsdom 기본 미지원
// eslint-disable-next-line @typescript-eslint/no-explicit-any
;(globalThis as any).ResizeObserver = ResizeObserverMock
// eslint-disable-next-line @typescript-eslint/no-explicit-any
;(globalThis as any).IntersectionObserver = IntersectionObserverMock

// window.scrollTo 폴리필 (jsdom 미지원 — vue-router scrollBehavior 의존)
window.scrollTo = vi.fn() as unknown as typeof window.scrollTo

// matchMedia 폴리필 (반응형 컴포넌트 의존)
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: vi.fn().mockImplementation((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  })),
})

// 각 테스트 사이 LocalStorage 격리
beforeEach(() => {
  localStorage.clear()
})
