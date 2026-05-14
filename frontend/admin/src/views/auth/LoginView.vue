<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4">
    <div class="w-full max-w-sm">
      <!-- 카드 -->
      <div class="rounded-xl bg-white p-8 shadow-md">
        <!-- 헤딩 — KWCAG 1.3.1 정보와 관계, 2.4.6 제목과 레이블 -->
        <h1 class="mb-6 text-center text-2xl font-bold text-gray-900">
          {{ t('auth.login.title') }}
        </h1>

        <!-- 성공/안내 알림 — 비밀번호 변경·세션 만료 후 리다이렉트 시 표시 -->
        <el-alert
          v-if="noticeMessage"
          :title="noticeMessage"
          :type="noticeType"
          show-icon
          :closable="false"
          role="status"
          class="mb-4"
          data-testid="login-notice"
        />

        <!-- 에러 알림 — KWCAG 4.1.3 상태 메시지 -->
        <el-alert
          v-if="errorMessage"
          :title="errorMessage"
          type="error"
          show-icon
          :closable="false"
          role="alert"
          aria-live="polite"
          class="mb-4"
          data-testid="login-error"
        />

        <!-- 폼 — KWCAG 3.3.1 오류 식별 -->
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleSubmit"
          :aria-label="t('auth.login.formLabel')"
        >
          <!-- 사용자명 -->
          <el-form-item
            prop="username"
            :label="t('auth.login.username')"
            class="mb-4"
          >
            <el-input
              id="username"
              v-model="form.username"
              name="username"
              autocomplete="username"
              :placeholder="t('auth.login.usernamePlaceholder')"
              :disabled="loading"
              clearable
              aria-required="true"
              :aria-describedby="usernameErrorId"
            />
          </el-form-item>

          <!-- 비밀번호 -->
          <el-form-item
            prop="password"
            :label="t('auth.login.password')"
            class="mb-6"
          >
            <el-input
              id="password"
              v-model="form.password"
              name="password"
              type="password"
              autocomplete="current-password"
              :placeholder="t('auth.login.passwordPlaceholder')"
              :disabled="loading"
              show-password
              aria-required="true"
              :aria-describedby="passwordErrorId"
            />
          </el-form-item>

          <!-- 제출 버튼 — KWCAG 4.1.2 이름, 역할, 값 -->
          <el-button
            type="primary"
            native-type="submit"
            :loading="loading"
            :disabled="loading"
            class="w-full"
            size="large"
            :aria-disabled="loading"
            :aria-label="loading ? t('auth.login.submitting') : t('auth.login.submit')"
          >
            {{ loading ? t('auth.login.submitting') : t('auth.login.submit') }}
          </el-button>

          <!-- 비밀번호 찾기 링크 — KWCAG 2.4.4 링크 목적 -->
          <div class="mt-4 text-center">
            <router-link
              to="/forgot-password"
              class="text-sm text-blue-600 hover:underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-blue-600 focus-visible:outline-offset-2"
            >
              {{ t('auth.login.forgotPassword') }}
            </router-link>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import type { ApiError } from '@iroum/shared/types/api'

const { t } = useI18n()
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()

// ── 폼 상태 ──────────────────────────────────────────────────────────────────
const formRef = ref<FormInstance>()
const loading = ref(false)
const errorMessage = ref('')

// ── URL query reason 처리 — 비밀번호 변경·재설정·세션 만료 후 리다이렉트 안내 ─
const noticeMessage = computed<string>(() => {
  const reason = route.query.reason as string | undefined
  if (reason === 'password_changed') return t('login.notice.passwordChanged')
  if (reason === 'password_reset') return t('auth.forgotPassword.success')
  if (reason === 'session_expired') return t('login.notice.sessionExpired')
  return ''
})

const noticeType = computed<'success' | 'info'>(() => {
  const reason = route.query.reason as string | undefined
  if (reason === 'password_changed' || reason === 'password_reset') return 'success'
  return 'info'
})

const form = reactive({ username: '', password: '' })

// ── 폼 유효성 규칙 — KWCAG 3.3.1 오류 식별 ──────────────────────────────────
const rules: FormRules = {
  username: [
    { required: true, message: t('auth.login.error.usernameRequired'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: t('auth.login.error.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('auth.login.error.passwordMin'), trigger: 'blur' },
  ],
}

// 에러 메시지 id (aria-describedby용)
const usernameErrorId = 'username-error'
const passwordErrorId = 'password-error'

// ── 제출 핸들러 ───────────────────────────────────────────────────────────────
async function handleSubmit(): Promise<void> {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMessage.value = ''

  try {
    await auth.login(form.username, form.password)

    // 로그인 성공 → redirect query 또는 대시보드 (HIGH-11: Open Redirect 방지)
    const redirect = route.query.redirect as string | undefined
    router.push(sanitizeRedirect(redirect))
  } catch (err) {
    errorMessage.value = resolveErrorMessage(err)
    // 에러 포커스 이동 — KWCAG 3.3.1
    focusError()
  } finally {
    loading.value = false
  }
}

// ── 리다이렉트 URL 검증 — HIGH-11 Open Redirect 취약점 차단 ───────────────────
// 외부 사이트(`http://evil.com`, `//evil.com`) 또는 protocol-relative URL 을
// `?redirect=` 쿼리 파라미터로 주입하는 공격을 방지한다.
// 안전한 상대 경로(`/dashboard`, `/users` 등)만 허용하고, 그 외는 모두 `/dashboard` 로 폴백.
function sanitizeRedirect(redirect: string | undefined): string {
  const fallback = '/dashboard'
  if (!redirect || typeof redirect !== 'string') return fallback
  const trimmed = redirect.trim()
  if (trimmed.length === 0) return fallback
  // 절대 URL 차단: http://, https:// 로 시작
  const lower = trimmed.toLowerCase()
  if (lower.startsWith('http://') || lower.startsWith('https://')) return fallback
  // protocol-relative URL 차단: // 로 시작 (브라우저가 외부 호스트로 해석)
  if (trimmed.startsWith('//')) return fallback
  // 경로 기반 상대 URL 만 허용: `/` 로 시작해야 함
  if (!trimmed.startsWith('/')) return fallback
  return trimmed
}

// ── 에러 코드 → 메시지 변환 ──────────────────────────────────────────────────
function resolveErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as ApiError | undefined
    const status = err.response?.status
    const code = data?.code ?? ''

    if (status === 401 || code === 'AUTH_INVALID_CREDENTIALS') {
      return t('auth.login.error.invalid')
    }
    if (status === 423 || code === 'AUTH_ACCOUNT_LOCKED') {
      return t('auth.login.error.locked')
    }
  }
  return t('auth.login.error.network')
}

function focusError(): void {
  // 에러 메시지 영역으로 포커스 이동 (스크린리더 알림 보조)
  const alert = document.querySelector('[data-testid="login-error"]') as HTMLElement | null
  alert?.focus()
}
</script>

<style scoped>
/* 포커스 가시성 — KWCAG 2.4.7 포커스 가시성 */
:deep(.el-input__wrapper:focus-within) {
  box-shadow: 0 0 0 2px #2563eb;
}

:deep(.el-button:focus-visible) {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}
</style>
