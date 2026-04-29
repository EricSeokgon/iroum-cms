<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? t('users.add') : t('users.edit')"
    width="520px"
    :close-on-click-modal="false"
    :aria-label="mode === 'create' ? t('users.add') : t('users.edit')"
    @close="emit('close')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :aria-label="mode === 'create' ? t('users.add') : t('users.edit')"
    >
      <!-- 사용자명 (create만) -->
      <el-form-item
        :label="t('users.field.username')"
        prop="username"
        :required="mode === 'create'"
      >
        <el-input
          id="form-username"
          v-model="form.username"
          :disabled="mode === 'edit'"
          :placeholder="t('users.field.username')"
          autocomplete="username"
          aria-required="true"
        />
      </el-form-item>

      <!-- 이메일 -->
      <el-form-item :label="t('users.field.email')" prop="email">
        <el-input
          id="form-email"
          v-model="form.email"
          type="email"
          :placeholder="t('users.field.email')"
          autocomplete="email"
          aria-required="true"
        />
      </el-form-item>

      <!-- 비밀번호 (create만) -->
      <el-form-item
        v-if="mode === 'create'"
        :label="t('users.field.password')"
        prop="password"
      >
        <el-input
          id="form-password"
          v-model="form.password"
          type="password"
          :placeholder="t('users.passwordHint')"
          autocomplete="new-password"
          show-password
          aria-required="true"
          :aria-describedby="'password-hint'"
        />
        <p id="password-hint" class="mt-1 text-xs text-gray-500">
          {{ t('users.passwordHint') }}
        </p>
      </el-form-item>

      <!-- 이름 -->
      <el-form-item :label="t('users.field.name')" prop="name">
        <el-input
          id="form-name"
          v-model="form.name"
          :placeholder="t('users.field.name')"
          aria-required="true"
        />
      </el-form-item>

      <!-- 상태 (edit만) -->
      <el-form-item v-if="mode === 'edit'" :label="t('users.field.status')" prop="status">
        <el-select
          id="form-status"
          v-model="form.status"
          style="width: 100%"
          :aria-label="t('users.field.status')"
        >
          <el-option :label="t('users.status.ACTIVE')" value="ACTIVE" />
          <el-option :label="t('users.status.INACTIVE')" value="INACTIVE" />
          <el-option :label="t('users.status.LOCKED')" value="LOCKED" />
          <el-option :label="t('users.status.DELETED')" value="DELETED" />
        </el-select>
      </el-form-item>

      <!-- 역할 -->
      <el-form-item :label="t('users.field.roleCodes')" prop="roleCodes">
        <el-select
          id="form-roles"
          v-model="form.roleCodes"
          multiple
          collapse-tags
          style="width: 100%"
          :placeholder="t('users.field.roleCodes')"
          :aria-label="t('users.field.roleCodes')"
        >
          <el-option :label="t('users.role.SUPER_ADMIN')" value="SUPER_ADMIN" />
          <el-option :label="t('users.role.DEPT_ADMIN')" value="DEPT_ADMIN" />
          <el-option :label="t('users.role.EDITOR')" value="EDITOR" />
          <el-option :label="t('users.role.VIEWER')" value="VIEWER" />
        </el-select>
      </el-form-item>

      <!-- 에러 메시지 -->
      <div v-if="submitError" role="alert" class="mb-2 text-sm text-red-500">
        {{ submitError }}
      </div>
    </el-form>

    <template #footer>
      <div class="flex justify-end gap-2">
        <el-button @click="emit('close')">{{ t('common.cancel') }}</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ mode === 'create' ? t('users.add') : t('users.edit') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { usersApi } from '@/api/users'
import type { UserSummary, UserStatus } from '@iroum/shared/types/api'
import axios from 'axios'

const props = defineProps<{
  mode: 'create' | 'edit'
  user: UserSummary | null
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const { t } = useI18n()

const visible = ref(true)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const submitError = ref('')

// ── 폼 상태 ────────────────────────────────────────────────────────────────────
interface FormState {
  username: string
  email: string
  password: string
  name: string
  status: UserStatus
  roleCodes: string[]
}

const form = reactive<FormState>({
  username: '',
  email: '',
  password: '',
  name: '',
  status: 'ACTIVE',
  roleCodes: [],
})

// 편집 모드 진입 시 기존 값 채우기
watch(
  () => props.user,
  (u) => {
    if (u && props.mode === 'edit') {
      form.username = u.username
      form.email = u.email
      form.name = u.name
      form.status = u.status
      form.roleCodes = []  // 상세 조회 없이 목록에서는 roleCodes 없음
    }
  },
  { immediate: true },
)

// ── 유효성 규칙 ────────────────────────────────────────────────────────────────
const rules: FormRules = {
  username: [
    { required: true, message: t('users.error.usernameRequired'), trigger: 'blur' },
    { min: 3, max: 50, message: t('users.error.usernameLength'), trigger: 'blur' },
  ],
  email: [
    { required: true, message: t('users.error.emailRequired'), trigger: 'blur' },
    { type: 'email', message: t('users.error.emailInvalid'), trigger: 'blur' },
  ],
  password: [
    { required: props.mode === 'create', message: t('users.error.passwordRequired'), trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (props.mode !== 'create') return callback()
        if (value.length < 8) return callback(new Error(t('users.error.passwordPolicy')))
        const hasUpper = /[A-Z]/.test(value)
        const hasLower = /[a-z]/.test(value)
        const hasNumber = /\d/.test(value)
        const hasSpecial = /[^A-Za-z0-9]/.test(value)
        const kinds = [hasUpper, hasLower, hasNumber, hasSpecial].filter(Boolean).length
        if (kinds < 3) return callback(new Error(t('users.error.passwordPolicy')))
        callback()
      },
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: t('users.error.nameRequired'), trigger: 'blur' },
  ],
  roleCodes: [
    { type: 'array', required: true, message: t('users.error.roleRequired'), trigger: 'change' },
  ],
}

// ── 제출 ────────────────────────────────────────────────────────────────────────

// @MX:WARN: [AUTO] handleSubmit — 409/400 에러를 사용자 친화적 메시지로 분기
// @MX:REASON: HTTP 상태 코드별 분기 처리 누락 시 백엔드 에러 메시지가 그대로 노출됨
async function handleSubmit(): Promise<void> {
  await formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    submitError.value = ''
    try {
      if (props.mode === 'create') {
        await usersApi.create({
          username: form.username,
          email: form.email,
          password: form.password,
          name: form.name,
          status: form.status,
          roleCodes: form.roleCodes,
        })
        ElMessage.success(t('users.success.created'))
      } else {
        if (!props.user) return
        await usersApi.update(props.user.id, {
          email: form.email,
          name: form.name,
          status: form.status,
          roleCodes: form.roleCodes,
        })
        ElMessage.success(t('users.success.updated'))
      }
      emit('saved')
    } catch (err) {
      if (axios.isAxiosError(err)) {
        const status = err.response?.status
        const code = err.response?.data?.code ?? ''
        if (status === 409 && code === 'DUPLICATE_USERNAME') {
          submitError.value = t('users.error.duplicateUsername')
        } else if (status === 409 && code === 'DUPLICATE_EMAIL') {
          submitError.value = t('users.error.duplicateEmail')
        } else if (status === 400) {
          submitError.value = t('users.error.passwordPolicy')
        } else {
          submitError.value = t('users.error.saveFailed')
        }
      } else {
        submitError.value = t('users.error.saveFailed')
      }
    } finally {
      submitting.value = false
    }
  })
}
</script>
