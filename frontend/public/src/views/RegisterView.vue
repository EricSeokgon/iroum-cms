<!--
  SPEC-CMS-PUBLIC-001 §6.16 — 회원가입 페이지
  - 이메일/비밀번호/비밀번호확인/이름 입력 후 authStore.register 호출
  - 클라이언트 검증: 이메일 형식, 비밀번호 8자 이상, 비밀번호 일치
  - 409 Conflict → 이메일 중복 안내, 그 외 → 일반 오류
  - 성공 시 router.query.redirect 또는 '/' 로 리다이렉트
-->
<template>
  <section class="flex min-h-screen items-center justify-center bg-surface-muted p-8">
    <div class="w-full max-w-md rounded-lg bg-white p-8 shadow">
      <h1 class="mb-6 text-center text-2xl font-bold text-content-DEFAULT">
        {{ t('register.title') }}
      </h1>

      <form class="space-y-4" novalidate @submit.prevent="onSubmit">
        <div>
          <label for="reg-email" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('register.email') }}
          </label>
          <input
            id="reg-email"
            v-model.trim="email"
            type="email"
            required
            autocomplete="email"
            :disabled="loading"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <div>
          <label for="reg-name" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('register.name') }}
          </label>
          <input
            id="reg-name"
            v-model.trim="name"
            type="text"
            required
            autocomplete="name"
            :disabled="loading"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <div>
          <label for="reg-password" class="mb-1 block text-sm font-medium text-content-DEFAULT">
            {{ t('register.password') }}
          </label>
          <input
            id="reg-password"
            v-model="password"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
            :disabled="loading"
            class="w-full rounded-md border border-gray-300 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-primary-500"
          />
        </div>

        <div>
          <label
            for="reg-password-confirm"
            class="mb-1 block text-sm font-medium text-content-DEFAULT"
          >
            {{ t('register.passwordConfirm') }}
          </label>
          <input
            id="reg-password-confirm"
            v-model="passwordConfirm"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
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
          {{ loading ? t('common.loading') : t('register.submit') }}
        </button>
      </form>

      <p class="mt-6 text-center text-sm text-content-muted">
        {{ t('register.hasAccount') }}
        <router-link
          :to="{ path: '/login', query: route.query }"
          class="ml-1 font-medium text-primary-600 hover:text-primary-700"
        >
          {{ t('register.login') }}
        </router-link>
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { useAuthStore } from '@/stores/authStore'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const email = ref('')
const name = ref('')
const password = ref('')
const passwordConfirm = ref('')
const loading = ref(false)
const errorMsg = ref('')

// 간이 이메일 형식 검증 (RFC 5322 완전 준수는 아니나 일반적 UX에 충분)
const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function validate(): string | null {
  if (!email.value || !EMAIL_REGEX.test(email.value)) {
    return t('register.error.general')
  }
  if (!name.value) {
    return t('register.error.general')
  }
  if (password.value.length < 8) {
    return t('register.error.general')
  }
  if (password.value !== passwordConfirm.value) {
    return t('register.error.passwordMismatch')
  }
  return null
}

async function onSubmit(): Promise<void> {
  errorMsg.value = ''
  const validationError = validate()
  if (validationError) {
    errorMsg.value = validationError
    return
  }

  loading.value = true
  try {
    await authStore.register(email.value, password.value, name.value)
    try {
      await authStore.loadUser()
    } catch {
      // /auth/me 실패는 치명적이지 않음 — 토큰은 이미 저장됨
    }
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.push(redirect)
  } catch (err) {
    if (axios.isAxiosError(err) && err.response?.status === 409) {
      errorMsg.value = t('register.error.duplicate')
    } else {
      errorMsg.value = t('register.error.general')
    }
  } finally {
    loading.value = false
  }
}
</script>
