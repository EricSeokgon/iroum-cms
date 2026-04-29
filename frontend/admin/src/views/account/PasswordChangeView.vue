<template>
  <div class="mx-auto max-w-lg py-8">
    <!-- 페이지 제목 — KWCAG 2.4.6 제목과 레이블 -->
    <h2 class="mb-6 text-xl font-semibold text-gray-900">
      {{ t('account.password.title') }}
    </h2>

    <!-- 성공 알림 -->
    <el-alert
      v-if="success"
      :title="t('account.password.success')"
      type="success"
      :closable="false"
      role="status"
      class="mb-4"
      data-testid="success-alert"
    />

    <!-- 에러 알림 — KWCAG 4.1.3 상태 메시지 -->
    <el-alert
      v-if="errorMessage"
      :title="errorMessage"
      type="error"
      :closable="false"
      role="alert"
      aria-live="polite"
      class="mb-4"
      data-testid="error-alert"
    />

    <!-- 비밀번호 변경 폼 -->
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="160px"
      label-position="right"
      @submit.prevent="onSubmit"
      :aria-label="t('account.password.title')"
    >
      <!-- 현재 비밀번호 -->
      <el-form-item
        :label="t('account.password.current')"
        prop="currentPassword"
      >
        <el-input
          v-model="form.currentPassword"
          type="password"
          show-password
          autocomplete="current-password"
          :disabled="loading || success"
          data-testid="input-current"
        />
      </el-form-item>

      <!-- 새 비밀번호 -->
      <el-form-item
        :label="t('account.password.new')"
        prop="newPassword"
      >
        <el-input
          v-model="form.newPassword"
          type="password"
          show-password
          autocomplete="new-password"
          aria-describedby="password-hint"
          :disabled="loading || success"
          data-testid="input-new"
        />
        <!-- 정책 힌트 — KWCAG 3.3.2 레이블 또는 지시사항 -->
        <div id="password-hint" class="mt-1 text-xs text-gray-500">
          {{ t('account.password.policyHint') }}
        </div>
        <!-- 강도 미터 -->
        <PasswordStrengthMeter :password="form.newPassword" />
      </el-form-item>

      <!-- 새 비밀번호 확인 -->
      <el-form-item
        :label="t('account.password.confirm')"
        prop="confirmPassword"
      >
        <el-input
          v-model="form.confirmPassword"
          type="password"
          show-password
          autocomplete="new-password"
          :disabled="loading || success"
          data-testid="input-confirm"
        />
      </el-form-item>

      <!-- 버튼 영역 -->
      <el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          :loading="loading"
          :aria-disabled="loading"
          :disabled="loading || success"
          data-testid="btn-submit"
        >
          {{ t('account.password.submit') }}
        </el-button>
        <el-button
          :disabled="loading"
          @click="onCancel"
          data-testid="btn-cancel"
        >
          {{ t('common.cancel') }}
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup lang="ts">
// @MX:NOTE: [AUTO] REQ-AUTH-009: 비밀번호 변경 성공 후 clearLocal() → /login?reason=password_changed
// 서버가 비밀번호 변경 API 응답 시 모든 refresh token을 무효화하므로 별도 logout API 불필요

import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'
import { authApi } from '@/api/auth'
import PasswordStrengthMeter from '@/components/PasswordStrengthMeter.vue'
import type { ApiError } from '@iroum/shared/types/api'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

// ── 폼 상태 ────────────────────────────────────────────────────────────────────
const formRef = ref<FormInstance>()
const loading = ref(false)
const success = ref(false)
const errorMessage = ref('')

const form = reactive({
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
})

// ── 비밀번호 정책 검증 (클라이언트 사전 검증) ─────────────────────────────────
// 8자 이상 + 대문자/소문자/숫자/특수문자 중 3종 이상
function validatePasswordPolicy(password: string): boolean {
  if (password.length < 8) return false
  let types = 0
  if (/[a-z]/.test(password)) types++
  if (/[A-Z]/.test(password)) types++
  if (/[0-9]/.test(password)) types++
  if (/[^A-Za-z0-9]/.test(password)) types++
  return types >= 3
}

// ── 폼 유효성 규칙 — KWCAG 3.3.1 오류 식별 ────────────────────────────────────
const rules: FormRules = {
  currentPassword: [
    { required: true, message: t('account.password.error.currentRequired'), trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: t('account.password.error.newRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!validatePasswordPolicy(value)) {
          callback(new Error(t('account.password.error.policy')))
        } else if (value === form.currentPassword && form.currentPassword !== '') {
          callback(new Error(t('account.password.error.sameAsCurrent')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: t('account.password.error.confirmRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== form.newPassword) {
          callback(new Error(t('account.password.error.confirmMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// ── 제출 핸들러 ────────────────────────────────────────────────────────────────
// @MX:WARN: [AUTO] 비밀번호 변경 후 clearLocal → 리다이렉트 순서 엄수 필요
// @MX:REASON: clearLocal 전에 라우터 이동 시 인증 가드가 /login 재진입을 막을 수 있음
async function onSubmit(): Promise<void> {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMessage.value = ''

  try {
    await authApi.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword,
    })

    // 성공: 알림 표시 후 3초 뒤 로그아웃 + 리다이렉트
    success.value = true
    setTimeout(async () => {
      auth.clearLocal()
      await router.push({ path: '/login', query: { reason: 'password_changed' } })
    }, 3000)
  } catch (err) {
    errorMessage.value = resolveErrorMessage(err)
  } finally {
    loading.value = false
  }
}

function onCancel(): void {
  router.push('/dashboard')
}

// ── 에러 코드 → 메시지 변환 ───────────────────────────────────────────────────
function resolveErrorMessage(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as ApiError | undefined
    const status = err.response?.status
    const code = data?.code ?? ''

    if (status === 401 || code === 'AUTH_INVALID_CREDENTIALS') {
      return t('account.password.error.currentMismatch')
    }
    if (code === 'PASSWORD_POLICY') {
      return t('account.password.error.policy')
    }
    if (code === 'PASSWORD_REUSE') {
      return t('account.password.error.reuse')
    }
  }
  return t('common.error.network')
}
</script>

<style scoped>
/* 포커스 가시성 — KWCAG 2.4.7 */
:deep(.el-input__wrapper:focus-within) {
  box-shadow: 0 0 0 2px #2563eb;
}
</style>
