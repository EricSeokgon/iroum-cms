<template>
  <div v-loading="loading" class="space-y-6">
    <!-- 헤더 -->
    <div class="flex items-center gap-3">
      <el-button :aria-label="t('common.back')" @click="goBack">
        <el-icon><i-ep-arrow-left /></el-icon>
        {{ t('common.back') }}
      </el-button>
      <h2 class="text-xl font-semibold text-gray-800">{{ t('survey.responses') }}</h2>
    </div>

    <!-- 응답 테이블 (AC-006) -->
    <el-table :data="rows" border data-testid="responses-table">
      <el-table-column prop="responseId" :label="t('survey.responseId')" width="120" />
      <el-table-column :label="t('survey.respondent')" min-width="160">
        <template #default="{ row }">
          <span data-testid="respondent-cell">{{ respondentLabel(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="t('survey.submittedAt')" min-width="200">
        <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="120" align="center">
        <template #default="{ row }">
          <el-button size="small" plain @click="openDetail(row)">
            {{ t('survey.viewDetail') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 페이지네이션 -->
    <div class="flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="size"
        :total="total"
        layout="prev, pager, next, total"
        background
        @current-change="onPageChange"
      />
    </div>

    <!-- 개별 응답 상세 드로어 (AC-008) -->
    <el-drawer v-model="drawerOpen" :title="t('survey.responseDetail')" size="480px">
      <div v-if="selected" class="space-y-4">
        <div class="text-sm text-gray-500">
          {{ t('survey.respondent') }}: {{ respondentLabel(selected) }}
        </div>
        <div
          v-for="(ans, idx) in selected.answers"
          :key="ans.questionId"
          class="rounded border border-gray-200 p-3"
        >
          <div class="mb-1 text-xs text-gray-400">Q{{ idx + 1 }}</div>
          <div class="text-sm font-medium text-gray-800">{{ ans.questionText }}</div>
          <div class="mt-1 text-sm text-gray-600">{{ ans.answerText ?? '-' }}</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getSurveyResponses, type SurveyResponseItem } from '@/api/survey'

interface Props {
  id: string
}
const props = defineProps<Props>()

const { t } = useI18n()
const router = useRouter()

const rows = ref<SurveyResponseItem[]>([])
const loading = ref(false)
const currentPage = ref(1)
const size = ref(20)
const total = ref(0)

const drawerOpen = ref(false)
const selected = ref<SurveyResponseItem | null>(null)

// 익명 설문/비로그인 응답은 "익명"으로 표기 (AC-007)
function respondentLabel(row: SurveyResponseItem): string {
  return row.respondentName ?? t('survey.anonymous')
}

function formatDateTime(value: string): string {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

async function loadResponses(): Promise<void> {
  loading.value = true
  try {
    const res = await getSurveyResponses(Number(props.id), currentPage.value - 1, size.value)
    rows.value = res.data.content
    total.value = res.data.totalElements
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function onPageChange(page: number): void {
  currentPage.value = page
  void loadResponses()
}

function openDetail(row: SurveyResponseItem): void {
  selected.value = row
  drawerOpen.value = true
}

function goBack(): void {
  router.back()
}

onMounted(loadResponses)
</script>
