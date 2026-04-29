<template>
  <el-dialog
    v-model="visible"
    :title="mode === 'create' ? t('board.masters.add') : t('board.masters.edit')"
    width="560px"
    :close-on-click-modal="false"
    :aria-label="mode === 'create' ? t('board.masters.add') : t('board.masters.edit')"
    @close="emit('close')"
  >
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="130px"
      :aria-label="mode === 'create' ? t('board.masters.add') : t('board.masters.edit')"
    >
      <!-- 코드 -->
      <el-form-item :label="t('board.masters.field.code')" prop="code">
        <el-input
          id="form-bbs-code"
          v-model="form.code"
          :disabled="mode === 'edit'"
          :placeholder="t('board.masters.field.codePlaceholder')"
          aria-required="true"
        />
      </el-form-item>

      <!-- 이름 -->
      <el-form-item :label="t('board.masters.field.name')" prop="name">
        <el-input
          id="form-bbs-name"
          v-model="form.name"
          :placeholder="t('board.masters.field.name')"
          aria-required="true"
        />
      </el-form-item>

      <!-- 설명 -->
      <el-form-item :label="t('board.masters.field.description')" prop="description">
        <el-input
          id="form-bbs-description"
          v-model="form.description"
          type="textarea"
          :rows="2"
          :placeholder="t('board.masters.field.description')"
        />
      </el-form-item>

      <!-- 유형 -->
      <el-form-item :label="t('board.masters.field.type')" prop="type">
        <el-select
          id="form-bbs-type"
          v-model="form.type"
          :disabled="mode === 'edit'"
          style="width: 180px"
          :aria-label="t('board.masters.field.type')"
        >
          <el-option
            v-for="opt in BBS_TYPES"
            :key="opt"
            :label="t(`board.masters.type.${opt}`)"
            :value="opt"
          />
        </el-select>
      </el-form-item>

      <!-- 댓글 허용 -->
      <el-form-item :label="t('board.masters.field.useComment')" prop="useComment">
        <el-switch
          v-model="form.useComment"
          :aria-label="t('board.masters.field.useComment')"
        />
      </el-form-item>

      <!-- 첨부파일 허용 -->
      <el-form-item :label="t('board.masters.field.useAttachment')" prop="useAttachment">
        <el-switch
          v-model="form.useAttachment"
          :aria-label="t('board.masters.field.useAttachment')"
        />
      </el-form-item>

      <!-- 최대 첨부파일 수 (첨부 허용 시) -->
      <el-form-item
        v-if="form.useAttachment"
        :label="t('board.masters.field.maxAttachmentCount')"
        prop="maxAttachmentCount"
      >
        <el-input-number
          v-model="form.maxAttachmentCount"
          :min="1"
          :max="10"
          :aria-label="t('board.masters.field.maxAttachmentCount')"
        />
      </el-form-item>

      <!-- 최대 파일 크기 (KB) -->
      <el-form-item
        v-if="form.useAttachment"
        :label="t('board.masters.field.maxAttachmentSizeKb')"
        prop="maxAttachmentSizeKb"
      >
        <el-input-number
          v-model="form.maxAttachmentSizeKb"
          :min="100"
          :max="102400"
          :step="1024"
          :aria-label="t('board.masters.field.maxAttachmentSizeKb')"
        />
        <span class="ml-2 text-sm text-gray-500">KB</span>
      </el-form-item>

      <!-- 익명 허용 -->
      <el-form-item :label="t('board.masters.field.allowAnonymous')" prop="allowAnonymous">
        <el-switch
          v-model="form.allowAnonymous"
          :aria-label="t('board.masters.field.allowAnonymous')"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="flex justify-end gap-2">
        <el-button @click="emit('close')">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ t('common.save') }}
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
import { boardApi } from '@/api/board'
import type { BbsMasterSummary, BbsMasterCreateRequest, BbsType } from '@iroum/shared/types/api'

const BBS_TYPES: BbsType[] = ['NORMAL', 'NOTICE', 'QNA', 'FAQ', 'GALLERY', 'PUBLICATION', 'SURVEY']

interface Props {
  mode: 'create' | 'edit'
  master?: BbsMasterSummary | null
}

const props = withDefaults(defineProps<Props>(), { master: null })
const emit = defineEmits<{ close: []; saved: [] }>()

const { t } = useI18n()
const formRef = ref<FormInstance>()
const saving = ref(false)
const visible = ref(true)

const form = reactive<BbsMasterCreateRequest>({
  code: '',
  name: '',
  description: '',
  type: 'NORMAL',
  useComment: true,
  useAttachment: false,
  maxAttachmentCount: 5,
  maxAttachmentSizeKb: 10240,
  allowAnonymous: false,
})

// 수정 모드: 기존 값으로 폼 초기화
watch(
  () => props.master,
  (m) => {
    if (m) {
      form.code = m.code
      form.name = m.name
      form.type = m.type
      form.useComment = m.useComment
      form.useAttachment = m.useAttachment
    }
  },
  { immediate: true },
)

const rules: FormRules = {
  code: [
    { required: true, message: t('board.masters.error.codeRequired'), trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_-]+$/, message: t('board.masters.error.codePattern'), trigger: 'blur' },
  ],
  name: [
    { required: true, message: t('board.masters.error.nameRequired'), trigger: 'blur' },
    { min: 1, max: 100, message: t('board.masters.error.nameLength'), trigger: 'blur' },
  ],
  type: [{ required: true, trigger: 'change' }],
}

async function handleSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (props.mode === 'create') {
      await boardApi.createMaster(form)
      ElMessage.success(t('board.masters.success.created'))
    } else if (props.master) {
      await boardApi.updateMaster(props.master.id, form)
      ElMessage.success(t('board.masters.success.updated'))
    }
    emit('saved')
  } catch {
    ElMessage.error(t('board.masters.error.saveFailed'))
  } finally {
    saving.value = false
  }
}
</script>
