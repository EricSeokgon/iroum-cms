<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? t('roles.form.createTitle') : t('roles.form.editTitle', { code: props.roleCode ?? '' })"
    width="560px"
    :close-on-click-modal="false"
    :aria-label="mode === 'create' ? t('roles.form.createTitle') : t('roles.form.editTitle', { code: props.roleCode ?? '' })"
    @close="emit('close')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
    >
      <!-- 역할 코드 (create만 활성) -->
      <el-form-item :label="t('roles.field.code')" prop="code">
        <el-input
          id="role-form-code"
          v-model="form.code"
          :disabled="mode === 'edit'"
          :placeholder="t('roles.form.codeHint')"
          autocomplete="off"
          aria-required="true"
          :aria-describedby="mode === 'create' ? 'role-code-hint' : undefined"
        />
        <p v-if="mode === 'create'" id="role-code-hint" class="mt-1 text-xs text-gray-500">
          {{ t('roles.form.codeHint') }}
        </p>
      </el-form-item>

      <!-- 역할 이름 -->
      <el-form-item :label="t('roles.field.name')" prop="name">
        <el-input
          id="role-form-name"
          v-model="form.name"
          :placeholder="t('roles.field.name')"
          aria-required="true"
        />
      </el-form-item>

      <!-- 설명 -->
      <el-form-item :label="t('roles.field.description')" prop="description">
        <el-input
          id="role-form-description"
          v-model="form.description"
          type="textarea"
          :rows="2"
          :placeholder="t('roles.field.description')"
        />
      </el-form-item>

      <!-- 시스템 역할 뱃지 (edit 모드에서 isSystem이면 표시) -->
      <el-form-item v-if="mode === 'edit' && isSystemRole" :label="t('roles.field.isSystem')">
        <el-tag type="warning" :aria-label="t('roles.field.isSystem')">
          {{ t('roles.field.isSystem') }}
        </el-tag>
        <p class="ml-2 text-xs text-gray-500">{{ t('roles.matrix.systemRoleReadonly') }}</p>
      </el-form-item>

      <!-- 권한 선택 (평면형 체크박스 그룹, resource별 그룹) -->
      <el-form-item :label="t('permissions.title')" prop="permissionCodes">
        <div
          v-if="loadingPermissions"
          class="text-sm text-gray-400"
          role="status"
          aria-live="polite"
        >
          {{ t('common.loading') }}
        </div>
        <div v-else class="w-full space-y-3">
          <div
            v-for="resource in sortedResources"
            :key="resource"
            class="rounded border border-gray-100 p-3"
          >
            <p class="mb-2 text-xs font-semibold text-gray-600 uppercase tracking-wide">
              {{ t(`permissions.resource.${resource}`, resource) }}
            </p>
            <div class="flex flex-wrap gap-3">
              <el-checkbox
                v-for="perm in permsByResource[resource]"
                :key="perm.code"
                v-model="form.permissionCodes"
                :label="perm.code"
                :disabled="isSystemRole && mode === 'edit'"
                :aria-label="`${t(`permissions.resource.${resource}`, resource)} — ${t(`permissions.action.${perm.action}`)}`"
              >
                {{ t(`permissions.action.${perm.action}`) }}
              </el-checkbox>
            </div>
          </div>
        </div>
      </el-form-item>

      <!-- 서버 에러 -->
      <div v-if="submitError" role="alert" aria-live="assertive" class="mb-2 text-sm text-red-500">
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
          {{ mode === 'create' ? t('roles.action.add') : t('roles.action.edit') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { rolesApi, permissionsApi } from '@/api/roles'
import type { PermissionSummary } from '@iroum/shared/types/api'
import axios from 'axios'

const props = defineProps<{
  mode: 'create' | 'edit'
  /** edit 모드일 때 편집 대상 역할 코드 */
  roleCode?: string
  /** edit 모드에서 시스템 역할 여부 */
  isSystem?: boolean
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
const loadingPermissions = ref(false)
const allPermissions = ref<PermissionSummary[]>([])
const isSystemRole = computed(() => props.isSystem ?? false)

// 리소스 순서
const RESOURCE_ORDER = ['USER', 'ORGANIZATION', 'ROLE', 'PERMISSION', 'AUDIT', 'SYSTEM']

interface FormState {
  code: string
  name: string
  description: string
  permissionCodes: string[]
}

const form = reactive<FormState>({
  code: '',
  name: '',
  description: '',
  permissionCodes: [],
})

// ── 권한 카탈로그 로드 ─────────────────────────────────────────────────────────
onMounted(async () => {
  loadingPermissions.value = true
  try {
    const [permsRes, detailRes] = await Promise.all([
      permissionsApi.list(),
      props.mode === 'edit' && props.roleCode
        ? rolesApi.detail(props.roleCode)
        : Promise.resolve(null),
    ])
    allPermissions.value = permsRes.data

    if (detailRes && props.mode === 'edit') {
      const detail = detailRes.data
      form.name = detail.name
      form.description = detail.description ?? ''
      form.permissionCodes = [...detail.permissionCodes]
    }
  } catch {
    // 권한 로드 실패는 사용자에게 표시
    ElMessage.error(t('common.error.unknown'))
  } finally {
    loadingPermissions.value = false
  }
})

// resource별 권한 그룹화
const permsByResource = computed<Record<string, PermissionSummary[]>>(() => {
  const map: Record<string, PermissionSummary[]> = {}
  for (const p of allPermissions.value) {
    if (!map[p.resource]) map[p.resource] = []
    map[p.resource].push(p)
  }
  return map
})

const sortedResources = computed<string[]>(() => {
  const resourceSet = new Set(allPermissions.value.map((p) => p.resource))
  const ordered = RESOURCE_ORDER.filter((r) => resourceSet.has(r))
  const rest = [...resourceSet].filter((r) => !RESOURCE_ORDER.includes(r)).sort()
  return [...ordered, ...rest]
})

// ── 유효성 규칙 ────────────────────────────────────────────────────────────────
const CODE_REGEX = /^[A-Z_]{3,50}$/

const rules: FormRules = {
  code: [
    {
      required: true,
      validator: (_rule, value: string, callback) => {
        if (props.mode === 'edit') return callback()
        if (!value) return callback(new Error(t('roles.error.invalidCode')))
        if (!CODE_REGEX.test(value)) return callback(new Error(t('roles.error.invalidCode')))
        callback()
      },
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: t('organizations.error.nameRequired'), trigger: 'blur' },
    { min: 1, max: 100, message: t('organizations.error.nameLength'), trigger: 'blur' },
  ],
}

// ── 제출 ────────────────────────────────────────────────────────────────────────

// @MX:WARN: [AUTO] handleSubmit — 409 DUPLICATE_ROLE_CODE 에러를 사용자 친화적으로 분기
// @MX:REASON: 백엔드가 400/409 에러 코드로 중복 코드, 시스템 역할 보호 오류를 반환
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  submitError.value = ''
  try {
    if (props.mode === 'create') {
      await rolesApi.create({
        code: form.code,
        name: form.name,
        description: form.description || undefined,
        permissionCodes: form.permissionCodes,
      })
      ElMessage.success(t('roles.success.created'))
    } else {
      if (!props.roleCode) return
      await rolesApi.update(props.roleCode, {
        name: form.name,
        description: form.description || undefined,
        permissionCodes: isSystemRole.value ? undefined : form.permissionCodes,
      })
      ElMessage.success(t('roles.success.updated'))
    }
    emit('saved')
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const code = err.response?.data?.code ?? ''
      if (code === 'DUPLICATE_ROLE_CODE') {
        submitError.value = t('roles.error.duplicateCode')
      } else if (code === 'INVALID_ROLE_CODE') {
        submitError.value = t('roles.error.invalidCode')
      } else if (code === 'SYSTEM_ROLE_PROTECTED') {
        submitError.value = t('roles.error.systemRoleProtected')
      } else {
        submitError.value = t('common.error.unknown')
      }
    } else {
      submitError.value = t('common.error.unknown')
    }
  } finally {
    submitting.value = false
  }
}
</script>
