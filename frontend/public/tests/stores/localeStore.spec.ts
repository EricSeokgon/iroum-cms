// SPEC-CMS-PUBLIC-001 T-003 / T-010 — localeStore 테스트 (E-05 / E-06 포함)
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// vue-i18n locale 변경을 추적하기 위한 mock
const i18nLocaleSetter = vi.fn()
vi.mock('@/i18n', () => ({
  default: {
    global: {
      locale: {
        get value() { return 'ko' },
        set value(v: string) { i18nLocaleSetter(v) },
      },
    },
  },
}))

// menuStore.reload 호출을 추적하기 위한 mock
const menuReloadMock = vi.fn().mockResolvedValue(undefined)
const menuFetchMock = vi.fn().mockResolvedValue(undefined)
vi.mock('@/stores/menuStore', () => ({
  useMenuStore: () => ({
    reload: menuReloadMock,
    fetchMenus: menuFetchMock,
    menus: [],
    isLoaded: false,
  }),
}))

describe('localeStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    i18nLocaleSetter.mockClear()
    menuReloadMock.mockClear()
    menuFetchMock.mockClear()
  })

  it('초기 상태는 ko', async () => {
    const { useLocaleStore } = await import('@/stores/localeStore')
    const store = useLocaleStore()
    expect(store.locale).toBe('ko')
  })

  it('setLocale은 locale을 변경하고 LocalStorage에 영속화한다', async () => {
    const { useLocaleStore } = await import('@/stores/localeStore')
    const store = useLocaleStore()
    store.setLocale('en')
    expect(store.locale).toBe('en')
    expect(localStorage.getItem('public.locale')).toBe('en')
  })

  it('initLocale은 LocalStorage 우선, 없으면 navigator.language 감지', async () => {
    localStorage.setItem('public.locale', 'en')
    const { useLocaleStore } = await import('@/stores/localeStore')
    const store = useLocaleStore()
    store.initLocale()
    expect(store.locale).toBe('en')
  })

  it('E-05: setLocale 호출 시 vue-i18n locale 이 동기화된다', async () => {
    const { useLocaleStore } = await import('@/stores/localeStore')
    const store = useLocaleStore()
    store.setLocale('en')
    expect(i18nLocaleSetter).toHaveBeenCalledWith('en')
  })

  it('E-06: locale 변경 시 menuStore.reload 가 새 lang 으로 호출된다', async () => {
    const { useLocaleStore } = await import('@/stores/localeStore')
    const store = useLocaleStore()
    store.setLocale('en')
    // 동적 import + .then 체인이 resolved 되도록 마이크로태스크 충분히 진행
    await vi.waitFor(() => {
      expect(menuReloadMock).toHaveBeenCalled()
    })
    expect(menuReloadMock).toHaveBeenCalledWith('en')
  })

  it('E-06: 동일 locale 재호출 시 menuStore.reload 는 호출되지 않는다 (불필요 트래픽 방지)', async () => {
    const { useLocaleStore } = await import('@/stores/localeStore')
    const store = useLocaleStore()
    // 초기 ko 상태에서 ko 재호출
    store.setLocale('ko')
    // 동적 import 가 처리될 시간을 충분히 제공 (변경 없을 시 호출 안됨)
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(menuReloadMock).not.toHaveBeenCalled()
  })
})
