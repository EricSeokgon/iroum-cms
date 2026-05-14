// Vitest 글로벌 셋업 — Element Plus 컴포넌트 렌더링용 polyfill 및 기본 플러그인
// @MX:NOTE: Element Plus는 jsdom에 없는 ResizeObserver/IntersectionObserver/matchMedia를 사용한다.
import { vi, beforeEach, afterEach } from 'vitest'
import { config } from '@vue/test-utils'
import ElementPlus from 'element-plus'

// 1) ResizeObserver polyfill — Element Plus 내부에서 참조
class ResizeObserverMock {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}
// jsdom 환경에서 누락된 ResizeObserver 주입
;(globalThis as unknown as { ResizeObserver: typeof ResizeObserverMock }).ResizeObserver =
  ResizeObserverMock

// 2) IntersectionObserver polyfill
class IntersectionObserverMock {
  root = null
  rootMargin = ''
  thresholds: number[] = []
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
  takeRecords(): IntersectionObserverEntry[] {
    return []
  }
}
;(
  globalThis as unknown as { IntersectionObserver: typeof IntersectionObserverMock }
).IntersectionObserver = IntersectionObserverMock

// 3) window.matchMedia polyfill — el-config-provider 등에서 참조
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

// 4) scrollTo polyfill
Object.defineProperty(window, 'scrollTo', {
  writable: true,
  value: vi.fn(),
})

// 5) Element Plus 일부 컴포넌트가 의존하는 getComputedStyle 보정
const originalGetComputedStyle = window.getComputedStyle
window.getComputedStyle = ((element: Element, pseudoElt?: string | null) => {
  const style = originalGetComputedStyle.call(window, element, pseudoElt ?? null)
  return style
}) as typeof window.getComputedStyle

// 6) 콘솔 경고 노이즈 감소 — Vue 경고는 보존, Element Plus 워닝만 무시
const originalWarn = console.warn
console.warn = (...args: unknown[]) => {
  const msg = String(args[0] ?? '')
  if (msg.includes('[Element Plus]')) return
  if (msg.includes('"ResizeObserver"')) return
  originalWarn(...(args as Parameters<typeof originalWarn>))
}

// 7) 글로벌 plugins — Element Plus를 모든 mount에 자동 설치
// @MX:NOTE: 일부 기존 테스트는 plugins에 ElementPlus를 별도로 추가하지만, 중복 설치는 안전하다.
config.global.plugins = [
  ...((config.global.plugins as unknown[]) ?? []),
  ElementPlus,
]

// 8) 글로벌 stub — transition은 비활성화하여 el-dialog 등 transition 내부 콘텐츠가 즉시 렌더링되도록
// @MX:NOTE: teleport 미stub, transition은 stub: false로 명시 → 실제 콘텐츠가 DOM에 즉시 노출됨
config.global.stubs = {
  ...(config.global.stubs as Record<string, unknown>),
  transition: false,
  'transition-group': false,
}

beforeEach(() => {
  // 각 테스트마다 mock 상태 초기화
  vi.clearAllMocks()
})

afterEach(() => {
  // spy 복원
  vi.restoreAllMocks()
})
