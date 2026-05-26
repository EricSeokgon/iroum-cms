<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="540px"
    :close-on-click-modal="false"
    :aria-label="dialogTitle"
    @close="emit('close')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="100px"
      :aria-label="dialogTitle"
    >
      <!-- 코드 (create 시만) -->
      <el-form-item
        v-if="mode !== 'edit'"
        :label="t('organizations.field.code')"
        prop="code"
      >
        <el-input
          id="org-code"
          v-model="form.code"
          :placeholder="t('organizations.field.codePlaceholder')"
          aria-required="true"
        />
        <p class="mt-1 text-xs text-gray-500">{{ t('organizations.field.codeHint') }}</p>
      </el-form-item>

      <!-- 이름 -->
      <el-form-item :label="t('organizations.field.name')" prop="name">
        <el-input
          id="org-name"
          v-model="form.name"
          :placeholder="t('organizations.field.name')"
          aria-required="true"
        />
      </el-form-item>

      <!-- 설명 -->
      <el-form-item :label="t('organizations.field.description')" prop="description">
        <el-input
          id="org-description"
          v-model="form.description"
          type="textarea"
          :rows="2"
          :placeholder="t('organizations.field.description')"
        />
      </el-form-item>

      <!-- 정렬 순서 -->
      <el-form-item :label="t('organizations.field.sortOrder')" prop="sortOrder">
        <el-input-number
          id="org-sort-order"
          v-model="form.sortOrder"
          :min="0"
          :max="9999"
          style="width: 160px"
          :aria-label="t('organizations.field.sortOrder')"
        />
      </el-form-item>

      <!-- 상태 (edit만) -->
      <el-form-item v-if="mode === 'edit'" :label="t('organizations.field.status')" prop="status">
        <el-select
          id="org-status"
          v-model="form.status"
          style="width: 100%"
          :aria-label="t('organizations.field.status')"
        >
          <el-option :label="t('organizations.status.ACTIVE')" value="ACTIVE" />
          <el-option :label="t('organizations.status.INACTIVE')" value="INACTIVE" />
          <el-option :label="t('organizations.status.DELETED')" value="DELETED" />
        </el-select>
      </el-form-item>

      <!-- 깊이 안내 (create-child) -->
      <div v-if="mode === 'create-child' && parentNode" class="mb-2 rounded bg-blue-50 px-3 py-2 text-sm text-blue-700">
        {{ t('organizations.field.parentId') }}: <strong>{{ parentNode.name }}</strong>
        ({{ t('organizations.field.depth') }}: {{ parentNode.depth + 1 }})
      </div>

      <!-- 깊이 초과 경고 -->
      <div v-if="depthExceeded" role="alert" class="mb-2 rounded bg-red-50 px-3 py-2 text-sm text-red-600">
        {{ t('organizations.error.depthExceeded') }}
      </div>

      <!-- 서버 에러 -->
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
          :disabled="depthExceeded"
          @click="handleSubmit"
        >
          {{ mode === 'edit' ? t('users.edit') : t('organizations.action.addRoot') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import axios from 'axios'
import { organizationsApi } from '@/api/organizations'
import type { OrganizationTreeNode, OrganizationStatus } from '@iroum/shared/types/api'

const props = defineProps<{
  mode: 'create-root' | 'create-child' | 'edit'
  parentNode?: OrganizationTreeNode | null
  editId?: number | null
}>()

const emit = defineEmits<{
  close: []
  saved: []
}>()

const { t } = useI18n()

const MAX_DEPTH = 5

const visible = ref(true)
const formRef = ref<FormInstance>()
const submitting = ref(false)
const submitError = ref('')

// ── 폼 상태 ────────────────────────────────────────────────────────────────────
interface FormState {
  code: string
  name: string
  description: string
  sortOrder: number
  status: OrganizationStatus
}

const form = reactive<FormState>({
  code: '',
  name: '',
  description: '',
  sortOrder: 0,
  status: 'ACTIVE',
})

// 깊이 초과 체크
const depthExceeded = computed(() => {
  if (props.mode === 'create-root') return false
  if (props.mode === 'create-child' && props.parentNode) {
    return props.parentNode.depth + 1 >= MAX_DEPTH
  }
  return false
})

// 다이얼로그 제목
const dialogTitle = computed(() => {
  if (props.mode === 'create-root') return t('organizations.action.addRoot')
  if (props.mode === 'create-child') return t('organizations.action.addChild')
  return t('organizations.action.edit')
})

// 유효성 규칙
const rules = computed<FormRules>(() => ({
  code: props.mode !== 'edit' ? [
    { required: true, message: t('organizations.error.codeRequired'), trigger: 'blur' },
    {
      validator: (_rule: unknown, value: string, callback: (err?: Error) => void) => {
        if (!/^[A-Za-z0-9_-]+$/.test(value)) {
          callback(new Error(t('organizations.error.codePattern')))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ] : [],
  name: [
    { required: true, message: t('organizations.error.nameRequired'), trigger: 'blur' },
    { min: 1, max: 100, message: t('organizations.error.nameLength'), trigger: 'blur' },
  ],
  sortOrder: [
    { required: true, type: 'number', message: t('organizations.error.sortOrderRequired'), trigger: 'change' },
  ],
}))

// 편집 모드: 기존 값 로드
onMounted(async () => {
  if (props.mode === 'edit' && props.editId) {
    try {
      const res = await organizationsApi.detail(props.editId)
      const d = res.data
      form.name = d.name
      form.description = d.description ?? ''
      form.sortOrder = d.sortOrder
      form.status = d.status
    } catch {
      ElMessage.error(t('organizations.error.loadFailed'))
      emit('close')
    }
  }
})

// ── 제출 ────────────────────────────────────────────────────────────────────────

// @MX:WARN: [AUTO] handleSubmit — 409 duplicateCode 및 cyclicReference 분기, 서버 에러 직접 노출 방지
// @MX:REASON: 서버 409 코드별 한국어 메시지 매핑 없으면 영어 백엔드 메시지가 사용자에게 노출됨
async function handleSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (depthExceeded.value) return

  submitting.value = true
  submitError.value = ''

  try {
    if (props.mode === 'create-root') {
      await organizationsApi.create({
        code: form.code,
        name: form.name,
        description: form.description || undefined,
        parentId: null,
        sortOrder: form.sortOrder,
      })
      ElMessage.success(t('organizations.success.created'))
    } else if (props.mode === 'create-child') {
      await organizationsApi.create({
        code: form.code,
        name: form.name,
        description: form.description || undefined,
        parentId: props.parentNode?.id ?? null,
        sortOrder: form.sortOrder,
      })
      ElMessage.success(t('organizations.success.created'))
    } else if (props.mode === 'edit' && props.editId) {
      await organizationsApi.update(props.editId, {
        name: form.name,
        description: form.description || undefined,
        sortOrder: form.sortOrder,
        status: form.status,
      })
      ElMessage.success(t('organizations.success.updated'))
    }
    emit('saved')
  } catch (err) {
    if (axios.isAxiosError(err)) {
      const code = err.response?.data?.code ?? ''
      if (code === 'DUPLICATE_CODE') {
        submitError.value = t('organizations.error.duplicateCode')
      } else if (code === 'DEPTH_EXCEEDED') {
        submitError.value = t('organizations.error.depthExceeded')
      } else if (code === 'CYCLIC_REFERENCE') {
        submitError.value = t('organizations.error.cyclicReference')
      } else {
        submitError.value = t('organizations.error.saveFailed')
      }
    } else {
      submitError.value = t('organizations.error.saveFailed')
    }
  } finally {
    submitting.value = false
  }
}
</script>
