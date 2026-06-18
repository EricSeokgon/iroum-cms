<template>
  <div v-loading="loading" class="space-y-6">
    <!-- 헤더 -->
    <div class="flex items-center justify-between">
      <div class="flex items-center gap-3">
        <el-button :aria-label="t('common.back')" @click="goBack">
          <el-icon><i-ep-arrow-left /></el-icon>
          {{ t('common.back') }}
        </el-button>
        <h2 class="text-xl font-semibold text-gray-800">
          {{ result?.title ?? t('survey.results') }}
        </h2>
      </div>
      <el-button
        v-if="canExport"
        type="primary"
        plain
        :loading="exporting"
        data-testid="export-csv-btn"
        @click="handleExport"
      >
        {{ t('survey.exportCsv') }}
      </el-button>
    </div>

    <!-- 요약 카드 -->
    <div v-if="result" class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <el-card shadow="never">
        <div class="text-sm text-gray-500">{{ t('survey.field.responseCount') }}</div>
        <div class="mt-1 text-2xl font-semibold text-gray-800">
          {{ result.totalResponses.toLocaleString() }}
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="text-sm text-gray-500">{{ t('survey.field.questions') }}</div>
        <div class="mt-1 text-2xl font-semibold text-gray-800">
          {{ result.questions.length }}
        </div>
      </el-card>
      <el-card shadow="never">
        <div class="text-sm text-gray-500">{{ t('survey.completionRate') }}</div>
        <div class="mt-1 text-2xl font-semibold text-gray-800">{{ completionRate }}</div>
      </el-card>
    </div>

    <!-- 질문별 결과 -->
    <div v-if="result && result.questions.length > 0" class="space-y-4">
      <el-card
        v-for="(q, idx) in result.questions"
        :key="q.questionId"
        shadow="never"
        data-testid="question-result"
      >
        <div class="mb-3 flex items-start justify-between gap-3">
          <div>
            <div class="text-xs text-gray-400">Q{{ idx + 1 }}</div>
            <div class="text-sm font-semibold text-gray-800">{{ q.questionText }}</div>
          </div>
          <el-tag size="small" effect="plain">
            {{ t(`survey.questionType.${q.questionType}`) }}
          </el-tag>
        </div>

        <!-- TEXT: 자유 응답 목록 (차트 없음) -->
        <template v-if="q.questionType === 'TEXT'">
          <div class="text-sm text-gray-500">
            {{ t('survey.totalAnswers', { count: q.totalAnswers }) }}
          </div>
        </template>

        <!-- SINGLE/MULTI/RATING/DATE: 분포 막대 -->
        <template v-else>
          <div v-if="q.distribution.length === 0" class="text-sm text-gray-400">
            {{ t('survey.noResponse') }}
          </div>
          <div v-else class="space-y-2">
            <div
              v-for="(item, i) in q.distribution"
              :key="i"
              class="flex items-center gap-3"
            >
              <span class="w-28 shrink-0 truncate text-sm text-gray-600" :title="item.label">
                {{ item.label }}
              </span>
              <el-progress
                class="flex-1"
                :percentage="Math.round(item.percentage)"
                :stroke-width="14"
              />
              <span class="w-16 shrink-0 text-right text-xs text-gray-500">
                {{ item.count.toLocaleString() }}
              </span>
            </div>
          </div>
        </template>
      </el-card>
    </div>

    <!-- 빈 상태 (AC-023) -->
    <el-empty
      v-else-if="result"
      data-testid="empty-state"
      :description="t('survey.noResponse')"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { usePermissionStore } from '@/stores/permissionStore'
import {
  getSurveyResults,
  exportSurveyResults,
  type SurveyResultDto,
} from '@/api/survey'

interface Props {
  id: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()
const permissionStore = usePermissionStore()

const result = ref<SurveyResultDto | null>(null)
const loading = ref(false)
const exporting = ref(false)

// SURVEY:EXPORT 권한 보유자만 CSV 내보내기 버튼 노출 (AC-005)
const canExport = computed(() => permissionStore.hasPermission('SURVEY:EXPORT'))

const completionRate = computed(() => {
  if (!result.value || result.value.totalResponses === 0) return '0%'
  return '100%'
})

async function loadResults(): Promise<void> {
  loading.value = true
  try {
    const res = await getSurveyResults(Number(props.id))
    result.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

async function handleExport(): Promise<void> {
  exporting.value = true
  try {
    const blob = await exportSurveyResults(Number(props.id))
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `survey-${props.id}-results.csv`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error(t('survey.exportError'))
  } finally {
    exporting.value = false
  }
}

function goBack(): void {
  router.back()
}

onMounted(loadResults)
</script>
