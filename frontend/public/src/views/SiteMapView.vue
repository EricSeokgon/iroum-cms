<!--
  SPEC-CMS-PUBLIC-001 T-008 — 사이트맵 (메뉴 트리)
  - menuStore.menus 를 2~3 단계 중첩 ul/li 로 렌더링
  - KWCAG 2.2: <nav aria-label="사이트맵">, role 자연
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('sitemap.title') }}</h1>
      <p class="mt-1 text-sm text-content-muted">{{ t('sitemap.description') }}</p>
    </header>

    <nav :aria-label="t('sitemap.title')" data-testid="sitemap-nav">
      <ul class="space-y-4" data-testid="sitemap-root">
        <li v-for="menu in menus" :key="menu.id" class="border-b border-gray-200 pb-3">
          <a
            :href="menu.path"
            class="text-base font-semibold text-content-DEFAULT hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
          >
            {{ menu.name }}
          </a>
          <ul
            v-if="menu.children && menu.children.length > 0"
            class="mt-2 ml-4 space-y-1"
            :data-testid="`sitemap-children-${menu.id}`"
          >
            <li v-for="child in menu.children" :key="child.id">
              <a
                :href="child.path"
                class="text-sm text-content-muted hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
              >
                {{ child.name }}
              </a>
              <ul
                v-if="child.children && child.children.length > 0"
                class="mt-1 ml-4 space-y-1"
                :data-testid="`sitemap-grandchildren-${child.id}`"
              >
                <li v-for="grandchild in child.children" :key="grandchild.id">
                  <a
                    :href="grandchild.path"
                    class="text-xs text-content-muted hover:text-primary-600 focus-visible:outline-2 focus-visible:outline-primary-600"
                  >
                    {{ grandchild.name }}
                  </a>
                </li>
              </ul>
            </li>
          </ul>
        </li>
      </ul>
    </nav>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { storeToRefs } from 'pinia'
import { useMenuStore } from '@/stores/menuStore'

const { t } = useI18n()
const menuStore = useMenuStore()
const { menus } = storeToRefs(menuStore)

onMounted(() => {
  menuStore.fetchMenus()
})
</script>
