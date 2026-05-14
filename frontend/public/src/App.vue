<!--
  SPEC-CMS-PUBLIC-001 — App entry
  PublicLayout은 router 정의에서 부모 라우트로 래핑되므로 App.vue는 ConfigProvider만 담당.
  noLayout 라우트(/maintenance, /login, /error/*)는 router-view 단독 렌더.
-->
<template>
  <el-config-provider :locale="elementLocale">
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import koLocale from 'element-plus/es/locale/lang/ko'
import enLocale from 'element-plus/es/locale/lang/en'
import { useLocaleStore } from '@/stores/localeStore'
import { useMenuStore } from '@/stores/menuStore'
import { useMaintenanceStore } from '@/stores/maintenanceStore'
import { useAuthStore } from '@/stores/authStore'

const { locale } = useI18n()
const localeStore = useLocaleStore()
const menuStore = useMenuStore()
const maintenanceStore = useMaintenanceStore()
const authStore = useAuthStore()

const elementLocale = computed(() => (locale.value === 'ko' ? koLocale : enLocale))

onMounted(async () => {
  // 1차 부팅: locale 초기화 → auth 복원 → menu/maintenance 동시 로드
  localeStore.initLocale()
  authStore.initFromStorage()
  await Promise.allSettled([menuStore.fetchMenus(), maintenanceStore.checkMaintenance()])
})
</script>
