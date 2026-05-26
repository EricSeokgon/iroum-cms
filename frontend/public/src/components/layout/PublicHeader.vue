<!--
  SPEC-CMS-PUBLIC-001 §5.2 — PublicHeader
  KWCAG 2.2 AA: role="banner", nav aria-label, 햄버거 메뉴 aria-expanded
-->
<template>
  <header role="banner" class="sticky top-0 z-40 border-b border-gray-200 bg-white shadow-sm">
    <div class="mx-auto flex h-14 max-w-screen-xl items-center justify-between px-4">
      <a
        href="/"
        class="text-lg font-bold text-content-DEFAULT focus-visible:outline-2 focus-visible:outline-primary-600"
        :aria-label="t('app.title')"
      >
        iroum-cms
      </a>

      <!-- 데스크탑 메인 메뉴 (md 이상) -->
      <nav
        role="navigation"
        :aria-label="t('common.mainMenu')"
        class="hidden md:block"
      >
        <ul class="flex items-center gap-6">
          <li v-for="menu in menus" :key="menu.id">
            <a
              :href="menu.url || '/'"
              class="text-sm font-medium text-content-DEFAULT hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
            >
              {{ menu.name }}
            </a>
          </li>
        </ul>
      </nav>

      <!-- 우측: 검색 + 언어 토글 + (모바일) 햄버거 -->
      <div class="flex items-center gap-3">
        <!-- 검색 입력 + 히스토리 드롭다운 (D-04) -->
        <form
          class="relative hidden md:block"
          role="search"
          :aria-label="t('common.search')"
          data-testid="header-search-form"
          @submit.prevent="onSearch"
        >
          <input
            v-model="searchInput"
            type="search"
            class="h-8 w-48 rounded-md border border-gray-300 bg-white px-3 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
            :placeholder="t('common.search')"
            :aria-label="t('common.search')"
            data-testid="header-search-input"
            @focus="historyOpen = true"
            @blur="onSearchBlur"
          />
          <SearchHistoryDropdown
            :open="historyOpen && !searchInput"
            :items="history"
            @select="onSelectHistory"
            @remove="onRemoveHistory"
            @clear-all="onClearAllHistory"
          />
        </form>

        <button
          type="button"
          class="rounded px-2 py-1 text-sm focus-visible:outline-2 focus-visible:outline-primary-600"
          :aria-pressed="localeStore.locale === 'en'"
          :aria-label="t('locale.toggle')"
          @click="toggleLocale"
        >
          <span :class="{ 'font-bold': localeStore.locale === 'ko' }">{{ t('locale.ko') }}</span>
          <span class="mx-1 text-gray-400">|</span>
          <span :class="{ 'font-bold': localeStore.locale === 'en' }">{{ t('locale.en') }}</span>
        </button>

        <!-- 인증 영역: 비인증 시 로그인 링크, 인증 시 사용자명 + 내 정보 + 로그아웃 -->
        <template v-if="authStore.isAuthenticated">
          <router-link
            :to="{ name: 'me' }"
            class="hidden text-sm text-content-muted hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600 md:inline"
            data-testid="header-user-name"
          >
            {{ displayName }}
          </router-link>
          <button
            type="button"
            class="rounded px-2 py-1 text-sm text-content-DEFAULT hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
            data-testid="header-logout"
            @click="onLogout"
          >
            {{ t('common.logout') }}
          </button>
        </template>
        <template v-else>
          <button
            type="button"
            class="rounded px-2 py-1 text-sm text-content-DEFAULT hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
            data-testid="header-login"
            @click="onLogin"
          >
            {{ t('common.login') }}
          </button>
          <router-link
            to="/register"
            class="rounded px-2 py-1 text-sm font-medium text-primary-600 hover:text-primary-700 focus-visible:outline-2 focus-visible:outline-primary-600"
            data-testid="header-register"
          >
            {{ t('common.register') }}
          </router-link>
        </template>

        <!-- 모바일 햄버거 -->
        <button
          type="button"
          class="rounded p-2 md:hidden focus-visible:outline-2 focus-visible:outline-primary-600"
          :aria-label="mobileOpen ? t('common.closeMenu') : t('common.openMenu')"
          :aria-expanded="mobileOpen"
          aria-controls="mobile-menu"
          @click="mobileOpen = !mobileOpen"
        >
          <svg width="20" height="20" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
            <path d="M3 5h14M3 10h14M3 15h14" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 모바일 오버레이 메뉴 -->
    <nav
      v-if="mobileOpen"
      id="mobile-menu"
      role="navigation"
      :aria-label="t('common.mainMenu')"
      class="border-t border-gray-200 bg-white md:hidden"
    >
      <ul class="flex flex-col">
        <li v-for="menu in menus" :key="menu.id">
          <a
            :href="menu.url || '/'"
            class="block px-4 py-3 text-sm font-medium text-content-DEFAULT hover:bg-gray-50 focus-visible:outline-2 focus-visible:outline-primary-600"
            @click="mobileOpen = false"
          >
            {{ menu.name }}
          </a>
        </li>
      </ul>
    </nav>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useLocaleStore } from '@/stores/localeStore'
import { useMenuStore } from '@/stores/menuStore'
import { useAuthStore } from '@/stores/authStore'
import { storeToRefs } from 'pinia'
import SearchHistoryDropdown from '@/components/search/SearchHistoryDropdown.vue'

const { t } = useI18n()
const router = useRouter()
const localeStore = useLocaleStore()
const menuStore = useMenuStore()
const authStore = useAuthStore()
const { menus } = storeToRefs(menuStore)

// 사용자 표시명: user.name → user.username → 기본값
const displayName = computed(() => {
  const u = authStore.user
  return u?.name ?? u?.username ?? t('common.memberFallback')
})

async function onLogin(): Promise<void> {
  await router.push({ name: 'login' })
}

async function onLogout(): Promise<void> {
  await authStore.logout()
  await router.push('/')
}

const mobileOpen = ref(false)

// D-04: 검색 입력 + 히스토리
const HISTORY_KEY = 'public.search.history'
const HISTORY_MAX = 5
const searchInput = ref('')
const historyOpen = ref(false)
const history = ref<string[]>([])

function loadHistory(): void {
  try {
    const raw = localStorage.getItem(HISTORY_KEY)
    if (!raw) return
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      history.value = parsed.filter((q): q is string => typeof q === 'string').slice(0, HISTORY_MAX)
    }
  } catch {
    history.value = []
  }
}

function saveHistory(): void {
  try {
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history.value))
  } catch {
    // localStorage 접근 실패 시 무시
  }
}

function pushHistory(q: string): void {
  const trimmed = q.trim()
  if (!trimmed) return
  // dedup: 동일 키워드는 제거 후 맨 앞에 추가
  history.value = [trimmed, ...history.value.filter((h) => h !== trimmed)].slice(0, HISTORY_MAX)
  saveHistory()
}

function onSearch(): void {
  const q = searchInput.value.trim()
  if (!q) return
  pushHistory(q)
  historyOpen.value = false
  searchInput.value = ''
  router.push({ name: 'search', query: { q } })
}

function onSelectHistory(q: string): void {
  pushHistory(q)
  historyOpen.value = false
  searchInput.value = ''
  router.push({ name: 'search', query: { q } })
}

function onRemoveHistory(q: string): void {
  history.value = history.value.filter((h) => h !== q)
  saveHistory()
}

function onClearAllHistory(): void {
  history.value = []
  saveHistory()
}

function onSearchBlur(): void {
  // 드롭다운 항목 클릭이 처리되도록 약간 지연
  setTimeout(() => {
    historyOpen.value = false
  }, 150)
}

function toggleLocale(): void {
  localeStore.setLocale(localeStore.locale === 'ko' ? 'en' : 'ko')
}

onMounted(() => {
  loadHistory()
})
</script>
