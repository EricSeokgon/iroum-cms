<template>
  <!-- 편집 충돌(낙관적 락 409) 모달 — SPEC-CMS-CONTENT-REVISION-001 M4 -->
  <el-dialog
    :model-value="visible"
    :title="t('revision.conflict.title')"
    width="440px"
    :close-on-click-modal="false"
    :show-close="false"
    align-center
    @close="emit('dismiss')"
  >
    <p class="text-sm text-gray-700">
      {{ t('revision.conflict.message', { version: currentVersion }) }}
    </p>

    <template #footer>
      <div class="flex justify-end gap-2">
        <el-button @click="emit('dismiss')">
          {{ t('revision.conflict.dismiss') }}
        </el-button>
        <el-button type="primary" @click="emit('reload')">
          {{ t('revision.conflict.reload') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{
  visible: boolean
  currentVersion: number
}>()

const emit = defineEmits<{
  (e: 'reload'): void
  (e: 'dismiss'): void
}>()

const { t } = useI18n()
</script>
