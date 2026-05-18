<template>
  <div>
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ t('ai.driftAlerts.title') }}
      </h2>
    </div>

    <!-- 빈 상태 -->
    <el-card v-if="!loading && alerts.length === 0">
      <el-empty :description="t('ai.driftAlerts.empty')" />
    </el-card>

    <!-- 알림 목록 -->
    <div v-else v-loading="loading" class="flex flex-col gap-3">
      <el-card
        v-for="alert in alerts"
        :key="alert.id"
        class="w-full"
      >
        <div class="flex flex-wrap items-center justify-between gap-4">
          <div class="flex flex-col gap-1">
            <span class="text-base font-semibold text-gray-800">
              {{ alert.modelName }}
            </span>
            <div class="flex flex-wrap gap-4 text-sm text-gray-600">
              <span>{{ t('ai.driftAlerts.field.predictionType') }}: {{ alert.predictionType }}</span>
              <span>{{ t('ai.driftAlerts.field.period') }}: {{ alert.periodStart }}</span>
              <span>
                {{ t('ai.driftAlerts.field.accuracy') }}:
                {{ alert.accuracy != null ? `${(alert.accuracy * 100).toFixed(1)}%` : '-' }}
              </span>
              <span>
                {{ t('ai.driftAlerts.field.rmse') }}:
                {{ alert.rmse != null ? alert.rmse.toFixed(3) : '-' }}
              </span>
            </div>
          </div>
          <el-button
            type="primary"
            :loading="submitting"
            @click="onRequestRetrain(alert)"
          >
            {{ t('ai.driftAlerts.requestRetrain') }}
          </el-button>
        </div>
      </el-card>
    </div>

    <!-- 재학습 확인 다이얼로그 -->
    <el-dialog
      v-model="showDialog"
      :title="t('ai.driftAlerts.dialog.title')"
      width="420px"
      destroy-on-close
    >
      <p class="text-sm text-gray-700">
        {{ t('ai.driftAlerts.dialog.confirm', { model: selectedAlert?.modelName ?? '' }) }}
      </p>
      <template #footer>
        <el-button @click="showDialog = false">
          {{ t('ai.driftAlerts.dialog.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="confirmRetrain"
        >
          {{ t('ai.driftAlerts.dialog.ok') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
// @MX:ANCHOR: [AUTO] DriftAlerts — 라우터, AdminLayout 사이드바, 테스트에서 참조
// @MX:REASON: fan_in >= 3: 라우터 등록, AdminLayout AI 메뉴, DriftAlerts.spec 테스트에서 참조
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElNotification } from 'element-plus'
import { aiAdminApi } from '@/api/aiAdminApi'
import { useAiMonitor } from '@/composables/useAiMonitor'
import type { AiDriftAlertDto } from '@/types/ai'

const { t } = useI18n()
const { run } = useAiMonitor()

// ── 상태 ────────────────────────────────────────────────────────────────────
const alerts = ref<AiDriftAlertDto[]>([])
const loading = ref(false)
const submitting = ref(false)
const showDialog = ref(false)
const selectedAlert = ref<AiDriftAlertDto | null>(null)

// ── 데이터 로드 ──────────────────────────────────────────────────────────────
async function loadAlerts(): Promise<void> {
  loading.value = true
  const data = await run(() => aiAdminApi.getDriftAlerts(), {
    errorMessage: t('ai.driftAlerts.loadError'),
  })
  // 최신 createdAt 내림차순 정렬
  alerts.value = (data ?? [])
    .slice()
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  loading.value = false
}

function onRequestRetrain(alert: AiDriftAlertDto): void {
  selectedAlert.value = alert
  showDialog.value = true
}

async function confirmRetrain(): Promise<void> {
  if (!selectedAlert.value) return
  submitting.value = true
  const result = await run(
    () =>
      aiAdminApi.requestRetrain({
        modelName: selectedAlert.value!.modelName,
        triggerReason: 'MANUAL',
      }),
    { errorMessage: t('ai.driftAlerts.retrainError') },
  )
  submitting.value = false
  showDialog.value = false
  if (result) {
    ElNotification({
      type: 'success',
      title: t('ai.driftAlerts.retrainSuccessTitle'),
      message: t('ai.driftAlerts.retrainSuccess', {
        model: selectedAlert.value.modelName,
      }),
    })
  }
}

onMounted(() => {
  loadAlerts()
})

defineExpose({ onRequestRetrain, confirmRetrain, alerts })
</script>
