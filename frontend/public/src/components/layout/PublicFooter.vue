<!--
  SPEC-CMS-PUBLIC-001 §5.2 — PublicFooter
  KWCAG: footer role="contentinfo"
-->
<template>
  <footer role="contentinfo" class="mt-auto border-t border-gray-200 bg-white">
    <div class="mx-auto max-w-screen-xl px-4 py-8">
      <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <div>
          <p class="text-sm font-semibold text-content-DEFAULT">iroum-cms</p>
          <p class="mt-2 text-sm text-content-muted">{{ t('footer.copyright') }}</p>
        </div>
        <nav :aria-label="t('footer.sitemap')">
          <ul class="space-y-2">
            <li>
              <router-link
                to="/sitemap"
                class="text-sm text-content-muted hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
              >
                {{ t('footer.sitemap') }}
              </router-link>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-content-muted hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
              >
                {{ t('footer.accessibility') }}
              </a>
            </li>
            <li>
              <a
                href="#"
                class="text-sm text-content-muted hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
              >
                {{ t('footer.privacy') }}
              </a>
            </li>
          </ul>
        </nav>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
