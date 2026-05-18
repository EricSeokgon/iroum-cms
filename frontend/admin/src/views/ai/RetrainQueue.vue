<template>
  <div>
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('ai.retrainQueue.title') }}
      </h2>
    </div>

    <!-- 수동 재학습 폼 -->
    <el-card class="mb-4">
      <form
        class="flex flex-wrap items-end gap-4"
        @submit.prevent="onManualRetrain"
      >
        <div class="flex flex-col gap-1">
          <label for="manual-model" class="text-sm font-medium text-gray-700">
            {{ t('ai.retrainQueue.manual.modelName') }}
          </label>
          <el-input
            id="manual-model"
            v-model="manualModelName"
            clearable
            style="width: 240px"
            :placeholder="t('ai.retrainQueue.manual.modelName')"
          />
        </div>
        <el-button
          type="primary"
          native-type="submit"
          :loading="submitting"
          :disabled="!manualModelName"
        >
          {{ t('ai.retrainQueue.manual.submit') }}
        </el-button>
      </form>
    </el-card>

    <!-- 큐 테이블 -->
    <el-table
      v-loading="loading"
      :data="items"
      stripe
      :empty-text="t('ai.retrainQueue.empty')"
      class="w-full"
    >
      <caption class="sr-only">{{ t('ai.retrainQueue.title') }}</caption>

      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column
        prop="modelName"
        :label="t('ai.retrainQueue.field.modelName')"
        min-width="160"
      />
      <el-table-column
        :label="t('ai.retrainQueue.field.triggerReason')"
        width="150"
      >
        <template #default="{ row }">
          <el-tag size="small">{{ row.triggerReason }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.retrainQueue.field.status')"
        width="140"
      >
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.retrainQueue.field.requestedAt')"
        min-width="180"
      >
        <template #default="{ row }">
          {{ formatDate(row.requestedAt) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('ai.retrainQueue.field.actions')"
        width="180"
      >
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'QUEUED'"
            size="small"
            type="primary"
            @click="onUpdateStatus(row.id, 'ACKNOWLEDGED')"
          >
            {{ t('ai.retrainQueue.action.acknowledge') }}
          </el-button>
          <el-button
            v-if="row.status === 'IN_PROGRESS'"
            size="small"
            type="success"
            @click="onUpdateStatus(row.id, 'DONE')"
          >
            {{ t('ai.retrainQueue.action.done') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
// @MX:ANCHOR: [AUTO] RetrainQueue — 라우터, AdminLayout 사이드바, 테스트에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, AdminLayout AI 메뉴, RetrainQueue.spec 테스트에서 참조
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { aiAdminApi } from '@/api/aiAdminApi'
import { useAiMonitor } from '@/composables/useAiMonitor'
import type { RetrainStatusDto } from '@/types/ai'

const { t } = useI18n()
const { run } = useAiMonitor()

// ── 상태 ────────────────────────────────────────────────────────────────────
const items = ref<RetrainStatusDto[]>([])
const loading = ref(false)
const submitting = ref(false)
const manualModelName = ref('')

// ── 뱃지 색상 매핑 ───────────────────────────────────────────────────────────
type TagType = 'info' | 'warning' | 'success' | 'danger' | 'primary'
function statusTagType(status: string): TagType {
  switch (status) {
    case 'QUEUED':
      return 'info'
    case 'IN_PROGRESS':
      return 'warning'
    case 'DONE':
      return 'success'
    case 'CANCELED':
      return 'danger'
    case 'ACKNOWLEDGED':
      return 'primary'
    default:
      return 'info'
  }
}

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadQueue(): Promise<void> {
  loading.value = true
  const data = await run(() => aiAdminApi.getRetrainQueue(), {
    errorMessage: t('ai.retrainQueue.loadError'),
  })
  items.value = data ?? []
  loading.value = false
}

async function onUpdateStatus(id: number, status: string): Promise<void> {
  const result = await run(
    () => aiAdminApi.updateRetrainStatus(id, { status }),
    { errorMessage: t('ai.retrainQueue.updateError') },
  )
  if (result) {
    ElMessage.success(t('ai.retrainQueue.updateSuccess'))
    loadQueue()
  }
}

async function onManualRetrain(): Promise<void> {
  if (!manualModelName.value) return
  submitting.value = true
  const result = await run(
    () =>
      aiAdminApi.requestRetrain({
        modelName: manualModelName.value,
        triggerReason: 'MANUAL',
      }),
    { errorMessage: t('ai.retrainQueue.manual.error') },
  )
  submitting.value = false
  if (result) {
    ElMessage.success(t('ai.retrainQueue.manual.success'))
    manualModelName.value = ''
    loadQueue()
  }
}

// ── 유틸 ────────────────────────────────────────────────────────────────────
function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

onMounted(() => {
  loadQueue()
})

defineExpose({ statusTagType, onUpdateStatus, onManualRetrain, manualModelName, items })
</script>
