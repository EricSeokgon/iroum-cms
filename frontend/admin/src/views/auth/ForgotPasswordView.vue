<template>
  <div class="flex min-h-screen items-center justify-center bg-gray-50 px-4">
    <div class="w-full max-w-md">
      <div class="rounded-xl bg-white p-8 shadow-md">
        <!-- 제목 — KWCAG 2.4.6 -->
        <h1 class="mb-2 text-center text-2xl font-bold text-gray-900">
          {{ t('auth.forgotPassword.title') }}
        </h1>

        <!-- 단계 표시기 — aria-label로 스크린리더 지원 -->
        <el-steps
          :active="currentStep - 1"
          finish-status="success"
          class="mb-8 mt-4"
          :aria-label="t('auth.forgotPassword.title')"
        >
          <el-step :title="t('auth.forgotPassword.step1.title')" />
          <el-step :title="t('auth.forgotPassword.step2.title')" />
          <el-step :title="t('auth.forgotPassword.step3.title')" />
        </el-steps>

        <!-- 전역 에러 알림 — KWCAG 4.1.3 -->
        <el-alert
          v-if="globalError"
          :title="globalError"
          type="error"
          show-icon
          :closable="false"
          role="alert"
          aria-live="polite"
          class="mb-4"
          data-testid="global-error"
        />

        <!-- ── Step 1: 이메일 입력 ─────────────────────────────────────────── -->
        <section v-if="currentStep === 1" aria-labelledby="step1-heading">
          <h2 id="step1-heading" class="sr-only">{{ t('auth.forgotPassword.step1.title') }}</h2>

          <el-form
            ref="step1FormRef"
            :model="step1Form"
            :rules="step1Rules"
            label-position="top"
            @submit.prevent="handleStep1Submit"
            :aria-label="t('auth.forgotPassword.step1.title')"
          >
            <el-form-item
              prop="email"
              :label="t('auth.forgotPassword.step1.label')"
            >
              <el-input
                id="fp-email"
                ref="emailInputRef"
                v-model="step1Form.email"
                type="email"
                name="email"
                autocomplete="email"
                :placeholder="t('auth.forgotPassword.step1.label')"
                :disabled="step1Loading"
                aria-required="true"
              />
            </el-form-item>

            <el-button
              type="primary"
              native-type="submit"
              :loading="step1Loading"
              :disabled="step1Loading"
              class="w-full"
              size="large"
            >
              {{ t('auth.forgotPassword.step1.submit') }}
            </el-button>
          </el-form>
        </section>

        <!-- ── Step 2: OTP 코드 입력 ──────────────────────────────────────── -->
        <section v-else-if="currentStep === 2" aria-labelledby="step2-heading">
          <h2 id="step2-heading" class="sr-only">{{ t('auth.forgotPassword.step2.title') }}</h2>

          <!-- 만료 카운트다운 -->
          <div
            class="mb-4 rounded-md bg-blue-50 px-4 py-2 text-sm text-blue-700"
            aria-live="polite"
            data-testid="expiry-countdown"
          >
            {{ expiryDisplay }}
          </div>

          <el-form
            ref="step2FormRef"
            :model="step2Form"
            :rules="step2Rules"
            label-position="top"
            @submit.prevent="handleStep2Submit"
            :aria-label="t('auth.forgotPassword.step2.title')"
          >
            <el-form-item
              prop="code"
              :label="t('auth.forgotPassword.step2.label')"
            >
              <el-input
                id="fp-code"
                ref="codeInputRef"
                v-model="step2Form.code"
                name="code"
                inputmode="numeric"
                autocomplete="one-time-code"
                maxlength="6"
                :placeholder="t('auth.forgotPassword.step2.label')"
                :disabled="step2Loading"
                aria-required="true"
                data-testid="otp-input"
                @input="onCodeInput"
              />
            </el-form-item>

            <!-- 시도 횟수 표시 -->
            <p
              v-if="attemptsLeft !== null && attemptsLeft < 3"
              class="mb-2 text-sm text-orange-600"
              role="alert"
              data-testid="attempts-left"
            >
              {{ t('auth.forgotPassword.step2.attemptsLeft', { n: attemptsLeft }) }}
            </p>

            <div class="flex gap-2">
              <el-button
                type="primary"
                native-type="submit"
                :loading="step2Loading"
                :disabled="step2Loading || step2Form.code.length < 6"
                class="flex-1"
                size="large"
              >
                {{ t('auth.forgotPassword.step2.submit') }}
              </el-button>

              <!-- 재발송 버튼 — cooldown 중 비활성 -->
              <el-button
                :disabled="resendCooldown > 0 || step2Loading"
                size="large"
                @click="handleResend"
                data-testid="resend-button"
              >
                <span v-if="resendCooldown > 0">
                  {{ t('auth.forgotPassword.step2.cooldown', { seconds: resendCooldown }) }}
                </span>
                <span v-else>{{ t('auth.forgotPassword.step2.resend') }}</span>
              </el-button>
            </div>
          </el-form>
        </section>

        <!-- ── Step 3: 새 비밀번호 입력 ───────────────────────────────────── -->
        <section v-else-if="currentStep === 3" aria-labelledby="step3-heading">
          <h2 id="step3-heading" class="sr-only">{{ t('auth.forgotPassword.step3.title') }}</h2>

          <el-form
            ref="step3FormRef"
            :model="step3Form"
            :rules="step3Rules"
            label-position="top"
            @submit.prevent="handleStep3Submit"
            :aria-label="t('auth.forgotPassword.step3.title')"
          >
            <el-form-item
              prop="newPassword"
              :label="t('auth.forgotPassword.step3.label')"
            >
              <el-input
                id="fp-new-password"
                ref="newPasswordInputRef"
                v-model="step3Form.newPassword"
                type="password"
                name="new-password"
                autocomplete="new-password"
                show-password
                :disabled="step3Loading"
                aria-required="true"
              />
              <PasswordStrengthMeter :password="step3Form.newPassword" />
            </el-form-item>

            <el-form-item
              prop="confirmPassword"
              :label="t('auth.forgotPassword.step3.confirmLabel')"
            >
              <el-input
                id="fp-confirm-password"
                v-model="step3Form.confirmPassword"
                type="password"
                name="confirm-password"
                autocomplete="new-password"
                show-password
                :disabled="step3Loading"
                aria-required="true"
              />
            </el-form-item>

            <el-button
              type="primary"
              native-type="submit"
              :loading="step3Loading"
              :disabled="step3Loading"
              class="w-full"
              size="large"
            >
              {{ t('auth.forgotPassword.step3.submit') }}
            </el-button>
          </el-form>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
// @MX:NOTE: [AUTO] REQ-AUTH-017: 3단계 이메일 OTP 비밀번호 재설정 흐름
// Step1(이메일) → Step2(OTP 확인, cooldown/만료) → Step3(새 비번) → /login?reason=password_reset
// @MX:WARN: [AUTO] 만료/시도초과 시 Step1으로 강제 복귀 — verifiedToken 무효화 처리 필수
// @MX:REASON: 만료된 requestId나 verifiedToken으로 Step2/3를 유지하면 사용자가 빈 공간에서 오류를 반복 경험

import { ref, reactive, computed, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import axios from 'axios'
import { authApi } from '@/api/auth'
import PasswordStrengthMeter from '@/components/PasswordStrengthMeter.vue'
import type { ApiError } from '@iroum/shared/types/api'

const { t } = useI18n()
const router = useRouter()

// ── 단계 상태 ──────────────────────────────────────────────────────────────────
const currentStep = ref<1 | 2 | 3>(1)
const globalError = ref('')

// ── Step 1 ─────────────────────────────────────────────────────────────────────
const step1FormRef = ref<FormInstance>()
const emailInputRef = ref()
const step1Loading = ref(false)
const step1Form = reactive({ email: '' })

const step1Rules: FormRules = {
  email: [
    { required: true, message: t('auth.forgotPassword.step1.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('auth.forgotPassword.step1.emailInvalid'), trigger: 'blur' },
  ],
}

// ── Step 2 상태 ────────────────────────────────────────────────────────────────
const step2FormRef = ref<FormInstance>()
const codeInputRef = ref()
const step2Loading = ref(false)
const step2Form = reactive({ code: '' })
const requestId = ref('')
const resendCooldown = ref(0)
const attemptsLeft = ref<number | null>(null)
const expiryAt = ref<Date | null>(null)

// 카운트다운 타이머들
let cooldownTimer: ReturnType<typeof setInterval> | null = null
let expiryTimer: ReturnType<typeof setInterval> | null = null

const expiryDisplay = computed(() => {
  if (!expiryAt.value) return ''
  const diff = Math.max(0, Math.floor((expiryAt.value.getTime() - Date.now()) / 1000))
  const m = Math.floor(diff / 60)
  const s = diff % 60
  return t('auth.forgotPassword.step2.expires', { minutes: m, seconds: String(s).padStart(2, '0') })
})

const step2Rules: FormRules = {
  code: [
    { required: true, message: t('auth.forgotPassword.step2.codeRequired'), trigger: 'blur' },
    { pattern: /^\d{6}$/, message: t('auth.forgotPassword.step2.codePattern'), trigger: 'blur' },
  ],
}

// ── Step 3 ─────────────────────────────────────────────────────────────────────
const step3FormRef = ref<FormInstance>()
const newPasswordInputRef = ref()
const step3Loading = ref(false)
const step3Form = reactive({ newPassword: '', confirmPassword: '' })
const verifiedToken = ref('')

const step3Rules: FormRules = {
  newPassword: [
    { required: true, message: t('auth.forgotPassword.step3.passwordRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!validatePasswordPolicy(value)) {
          callback(new Error(t('account.password.error.policy')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: t('auth.forgotPassword.step3.confirmRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value !== step3Form.newPassword) {
          callback(new Error(t('account.password.error.confirmMismatch')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

// ── 유틸리티 ───────────────────────────────────────────────────────────────────
function validatePasswordPolicy(password: string): boolean {
  if (password.length < 8) return false
  let types = 0
  if (/[a-z]/.test(password)) types++
  if (/[A-Z]/.test(password)) types++
  if (/[0-9]/.test(password)) types++
  if (/[^A-Za-z0-9]/.test(password)) types++
  return types >= 3
}

// 숫자만 입력 허용
function onCodeInput(): void {
  step2Form.code = step2Form.code.replace(/\D/g, '').slice(0, 6)
}

// cooldown 카운트다운 시작
function startCooldown(seconds: number): void {
  if (cooldownTimer) clearInterval(cooldownTimer)
  resendCooldown.value = seconds
  cooldownTimer = setInterval(() => {
    resendCooldown.value = Math.max(0, resendCooldown.value - 1)
    if (resendCooldown.value === 0 && cooldownTimer) {
      clearInterval(cooldownTimer)
      cooldownTimer = null
    }
  }, 1000)
}

// 만료 카운트다운 시작 — 만료 시 Step1 복귀
function startExpiryCountdown(expiresAt: string): void {
  if (expiryTimer) clearInterval(expiryTimer)
  expiryAt.value = new Date(expiresAt)

  expiryTimer = setInterval(() => {
    const remaining = (expiryAt.value!.getTime() - Date.now()) / 1000
    if (remaining <= 0) {
      clearInterval(expiryTimer!)
      expiryTimer = null
      globalError.value = t('auth.forgotPassword.error.expired')
      resetToStep1()
    }
  }, 5000) // 5초마다 체크 (aria-live 과다 갱신 방지)
}

function clearTimers(): void {
  if (cooldownTimer) { clearInterval(cooldownTimer); cooldownTimer = null }
  if (expiryTimer) { clearInterval(expiryTimer); expiryTimer = null }
}

// Step 1로 초기화
function resetToStep1(): void {
  clearTimers()
  requestId.value = ''
  verifiedToken.value = ''
  attemptsLeft.value = null
  expiryAt.value = null
  resendCooldown.value = 0
  step2Form.code = ''
  step3Form.newPassword = ''
  step3Form.confirmPassword = ''
  currentStep.value = 1
  nextTick(() => { emailInputRef.value?.focus() })
}

// 단계 전환 시 첫 input에 포커스 — KWCAG 2.4.3 포커스 순서
function focusFirstInput(step: 2 | 3): void {
  nextTick(() => {
    if (step === 2) codeInputRef.value?.focus()
    if (step === 3) newPasswordInputRef.value?.focus()
  })
}

// ── Step 1: 이메일 제출 → verify/request ──────────────────────────────────────
async function handleStep1Submit(): Promise<void> {
  if (!step1FormRef.value) return
  const valid = await step1FormRef.value.validate().catch(() => false)
  if (!valid) return

  step1Loading.value = true
  globalError.value = ''

  try {
    const res = await authApi.verifyRequest({
      channel: 'EMAIL',
      target: step1Form.email,
      purpose: 'PASSWORD_RESET',
    })
    requestId.value = res.data.requestId
    startCooldown(res.data.cooldownSeconds)
    startExpiryCountdown(res.data.expiresAt)
    currentStep.value = 2
    focusFirstInput(2)
  } catch (err) {
    globalError.value = resolveStep1Error(err)
  } finally {
    step1Loading.value = false
  }
}

// ── Step 2: OTP 확인 → verify/confirm ─────────────────────────────────────────
async function handleStep2Submit(): Promise<void> {
  if (!step2FormRef.value) return
  const valid = await step2FormRef.value.validate().catch(() => false)
  if (!valid) return

  step2Loading.value = true
  globalError.value = ''

  try {
    const res = await authApi.verifyConfirm({
      requestId: requestId.value,
      code: step2Form.code,
    })
    verifiedToken.value = res.data.verifiedToken
    clearTimers()
    currentStep.value = 3
    focusFirstInput(3)
  } catch (err) {
    const { message, goBack } = resolveStep2Error(err)
    globalError.value = message
    if (goBack) resetToStep1()
  } finally {
    step2Loading.value = false
  }
}

// ── 재발송 ────────────────────────────────────────────────────────────────────
async function handleResend(): Promise<void> {
  if (resendCooldown.value > 0) return
  globalError.value = ''

  try {
    const res = await authApi.verifyRequest({
      channel: 'EMAIL',
      target: step1Form.email,
      purpose: 'PASSWORD_RESET',
    })
    requestId.value = res.data.requestId
    startCooldown(res.data.cooldownSeconds)
    startExpiryCountdown(res.data.expiresAt)
    step2Form.code = ''
    attemptsLeft.value = null
  } catch (err) {
    globalError.value = resolveStep1Error(err)
  }
}

// ── Step 3: 새 비밀번호 제출 ──────────────────────────────────────────────────
async function handleStep3Submit(): Promise<void> {
  if (!step3FormRef.value) return
  const valid = await step3FormRef.value.validate().catch(() => false)
  if (!valid) return

  step3Loading.value = true
  globalError.value = ''

  try {
    await authApi.passwordResetConfirm({
      verifiedToken: verifiedToken.value,
      newPassword: step3Form.newPassword,
    })
    // 성공 → 3초 후 /login?reason=password_reset
    setTimeout(() => {
      router.push({ path: '/login', query: { reason: 'password_reset' } })
    }, 3000)
    // 성공 메시지를 globalError 자리에 표시 (type은 success이지만 globalError 재활용 대신 별도 표시)
    globalError.value = ''
    // step indicator 완료 표시를 위해 step을 유지하고 성공 배너 노출
    successVisible.value = true
  } catch (err) {
    const { message, goBack } = resolveStep3Error(err)
    globalError.value = message
    if (goBack) resetToStep1()
  } finally {
    step3Loading.value = false
  }
}

const successVisible = ref(false)

// ── 에러 처리 헬퍼 ────────────────────────────────────────────────────────────
function resolveStep1Error(err: unknown): string {
  if (axios.isAxiosError(err)) {
    const code = (err.response?.data as ApiError | undefined)?.code ?? ''
    const status = err.response?.status
    if (status === 429 || code === 'COOLDOWN_ACTIVE') {
      const seconds = (err.response?.data as Record<string, unknown>)?.cooldownSeconds as number | undefined
      return t('auth.forgotPassword.error.cooldown', { seconds: seconds ?? 60 })
    }
    if (status === 403 || code === 'IP_BLOCKED') {
      return t('auth.forgotPassword.error.ipBlocked')
    }
  }
  return t('common.error.network')
}

function resolveStep2Error(err: unknown): { message: string; goBack: boolean } {
  if (axios.isAxiosError(err)) {
    const data = err.response?.data as (ApiError & { attemptsLeft?: number }) | undefined
    const status = err.response?.status
    const code = data?.code ?? ''

    if (status === 401 || code === 'CODE_MISMATCH') {
      const n = data?.attemptsLeft ?? 0
      attemptsLeft.value = n
      return { message: t('auth.forgotPassword.error.codeMismatch', { n }), goBack: false }
    }
    if (status === 403 || code === 'ATTEMPT_EXCEEDED') {
      return { message: t('auth.forgotPassword.error.attemptExceeded'), goBack: true }
    }
    if (code === 'REQUEST_EXPIRED') {
      return { message: t('auth.forgotPassword.error.expired'), goBack: true }
    }
  }
  return { message: t('common.error.network'), goBack: false }
}

function resolveStep3Error(err: unknown): { message: string; goBack: boolean } {
  if (axios.isAxiosError(err)) {
    const code = (err.response?.data as ApiError | undefined)?.code ?? ''
    const status = err.response?.status
    if (code === 'PASSWORD_POLICY') return { message: t('auth.forgotPassword.error.policy'), goBack: false }
    if (code === 'PASSWORD_REUSE') return { message: t('auth.forgotPassword.error.reuse'), goBack: false }
    if (status === 401 || code === 'TOKEN_INVALID') {
      return { message: t('auth.forgotPassword.error.tokenInvalid'), goBack: true }
    }
  }
  return { message: t('common.error.network'), goBack: false }
}

// 컴포넌트 언마운트 시 타이머 정리
onUnmounted(clearTimers)
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

/* 스크린리더 전용 텍스트 */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border-width: 0;
}
</style>
