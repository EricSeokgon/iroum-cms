<template>
  <el-config-provider :locale="elementLocale">
    <!--
      AdminLayout이 인증 라우트의 헤더/사이드바/메인을 담당합니다.
      공개 라우트(login, 404)는 레이아웃 없이 router-view만 렌더합니다.
    -->
    <router-view />
  </el-config-provider>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import koLocale from 'element-plus/es/locale/lang/ko'
import enLocale from 'element-plus/es/locale/lang/en'

const { locale } = useI18n()

const elementLocale = computed(() =>
  locale.value === 'ko' ? koLocale : enLocale,
)
</script>
