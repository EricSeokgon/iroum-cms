// SPEC-CMS-PUBLIC-001 §5.4 — localeStore (ko/en 언어 토글)
// T-010 E-05/E-06: setLocale 호출 시 menuStore.reload() 트리거로 i18n 메뉴 라벨 갱신
import { defineStore } from 'pinia'
import { ref } from 'vue'
import i18n from '@/i18n'

export type LocaleCode = 'ko' | 'en'
const STORAGE_KEY = 'public.locale'

export const useLocaleStore = defineStore('locale', () => {
  const locale = ref<LocaleCode>('ko')

  function setLocale(next: LocaleCode): void {
    const changed = locale.value !== next
    locale.value = next
    localStorage.setItem(STORAGE_KEY, next)
    // vue-i18n과 동기화
    i18n.global.locale.value = next

    // E-06: locale 변경 시 menuStore.reload() — 서버 i18n 메뉴 라벨 재취득
    // 동적 import 로 순환 참조(localeStore -> menuStore -> apiClient -> router -> ...) 방지
    if (changed) {
      void import('@/stores/menuStore').then(({ useMenuStore }) => {
        try {
          const menuStore = useMenuStore()
          void menuStore.reload(next)
        } catch {
          // Pinia 미활성화 환경(예: 일부 단위 테스트) 에서는 silent fail
        }
      })
    }
  }

  function initLocale(): void {
    const stored = localStorage.getItem(STORAGE_KEY) as LocaleCode | null
    if (stored === 'ko' || stored === 'en') {
      setLocale(stored)
      return
    }
    // navigator.language 첫 감지 (1회만 — LocalStorage 미보유 시)
    const browserLang = typeof navigator !== 'undefined' ? navigator.language : 'ko'
    setLocale(browserLang.startsWith('en') ? 'en' : 'ko')
  }

  return { locale, setLocale, initLocale }
})
