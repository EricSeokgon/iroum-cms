<template>
  <el-config-provider :locale="elementLocale">
    <div class="min-h-screen bg-surface-muted">
      <!-- 스킵 내비게이션 — KWCAG 2.4.1 블록 건너뛰기 -->
      <a
        href="#main-content"
        class="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded focus:bg-primary-600 focus:px-4 focus:py-2 focus:text-white"
      >
        본문 바로가기
      </a>

      <!-- 헤더 -->
      <header role="banner" class="sticky top-0 z-40 border-b border-gray-200 bg-white shadow-sm">
        <div class="mx-auto flex h-14 max-w-screen-xl items-center justify-between px-4">
          <a href="/" class="flex items-center gap-2 text-lg font-bold text-content-DEFAULT">
            <span aria-hidden="true">⚙</span>
            <span>{{ t('app.title') }}</span>
          </a>
          <nav aria-label="주요 내비게이션">
            <router-link
              to="/health"
              class="rounded px-3 py-1 text-sm text-content-muted hover:bg-surface-subtle hover:text-content-DEFAULT focus-visible:outline-primary-600"
            >
              {{ t('nav.health') }}
            </router-link>
          </nav>
        </div>
      </header>

      <!-- 메인 콘텐츠 -->
      <main id="main-content" role="main" class="mx-auto max-w-screen-xl px-4 py-8">
        <router-view />
      </main>

      <!-- 푸터 -->
      <footer role="contentinfo" class="border-t border-gray-200 bg-white py-4 text-center text-sm text-content-subtle">
        &copy; {{ new Date().getFullYear() }} iroum-cms. All rights reserved.
      </footer>
    </div>
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import koLocale from 'element-plus/es/locale/lang/ko'
import enLocale from 'element-plus/es/locale/lang/en'

const { t, locale } = useI18n()

const elementLocale = computed(() =>
  locale.value === 'ko' ? koLocale : enLocale,
)
</script>
