<!--
  SPEC-CMS-PUBLIC-001 §6.16 — 로그인 페이지
  - 이메일/비밀번호 입력 후 authStore.login 호출
  - 성공 시 router.query.redirect 또는 '/' 로 리다이렉트
  - 실패 시 사용자 친화적 오류 메시지 표시
-->
<template>
  <section class="flex min-h-screen items-center justify-center bg-surface-muted p-8">
    <div class="w-full max-w-md rounded-lg bg-white p-8 shadow">
      <h1 class="mb-6 text-center text-2xl font-bold text-content-DEFAULT">
        {{ t('login.title') }}
      </h1>

      <form class="space-y-4" novalidate @submit.prevent="onSubmit">
        <div>
          <label for="login-email" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('login.email') }}
          </label>
          <input
            id="login-email"
            v-model="email"
            type="email"
            required
            autocomplete="username"
            :disabled="loading"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <div>
          <label for="login-password" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('login.password') }}
          </label>
          <input
            id="login-password"
            v-model="password"
            type="password"
            required
            autocomplete="current-password"
            :disabled="loading"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <p v-if="errorMsg" class="text-sm text-red-600" role="alert">{{ errorMsg }}</p>

        <button
          type="submit"
          :disabled="loading"
          class="w-full rounded-md bg-primary-600 px-4 py-2 text-sm font-medium text-white hover:bg-primary-700 disabled:opacity-50"
        >
          {{ loading ? t('common.loading') : t('login.submit') }}
        </button>
      </form>

      <p class="mt-6 text-center text-sm text-content-muted">
        {{ t('login.noAccount') }}
        <router-link
          :to="{ path: '/register', query: route.query }"
          class="ml-1 font-medium text-primary-600 hover:text-primary-700"
        >
          {{ t('login.register') }}
        </router-link>
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')

async function onSubmit(): Promise<void> {
  errorMsg.value = ''
  if (!email.value || !password.value) {
    errorMsg.value = t('login.error')
    return
  }
  loading.value = true
  try {
    // 백엔드는 username 필드를 이메일 형식으로도 수용 (SPEC-CMS-002)
    await authStore.login(email.value, password.value)
    try {
      await authStore.loadUser()
    } catch {
      // /auth/me 실패는 치명적이지 않음 — 토큰은 이미 저장됨
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch {
    errorMsg.value = t('login.error')
  } finally {
    loading.value = false
  }
}
</script>
