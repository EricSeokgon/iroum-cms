<template>
  <!-- 가이드라인 보고서 — SPEC-CMS-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">
        {{ store.currentReport?.title ?? '가이드라인 보고서' }}
      </h2>
      <div class="flex gap-2">
        <el-button @click="goMatch">매칭으로</el-button>
        <el-button type="primary" :loading="downloading" :icon="Download" @click="handleDownload">
          PDF 다운로드
        </el-button>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 좌측: 보고서 본문 -->
      <el-col :xs="24" :md="16">
        <el-card v-loading="store.reportLoading" shadow="never" class="mb-4">
          <div class="flex flex-wrap gap-3 text-xs text-gray-500 mb-3 pb-3 border-b">
            <span>상태:
              <el-tag size="small" :type="statusType(store.currentReport?.status)">
                {{ store.currentReport?.status ?? '-' }}
              </el-tag>
            </span>
            <span v-if="store.currentReport?.template_name">
              템플릿: {{ store.currentReport.template_name }}
            </span>
            <span v-if="store.currentReport?.created_at">
              생성: {{ formatDate(store.currentReport.created_at) }}
            </span>
          </div>
          <!-- 보고서 HTML 렌더링 — 서버에서 sanitize된 신뢰 가능한 컨텐츠 -->
          <div
            v-if="store.currentReport?.html_content"
            class="report-body prose max-w-none"
            v-html="sanitize(store.currentReport.html_content)"
          />
          <el-empty v-else description="보고서 내용이 없습니다" />
        </el-card>
      </el-col>

      <!-- 우측: 체크리스트 진행 -->
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="mb-4">
          <template #header>
            <div class="flex items-center justify-between">
              <span class="font-semibold">체크리스트 진행</span>
              <el-button size="small" link @click="loadChecklist">새로고침</el-button>
            </div>
          </template>

          <!-- 진행률 통계 -->
          <div v-if="store.checklistStats" class="mb-4">
            <p class="text-sm font-medium mb-2">
              완료율: {{ formatPct(store.checklistStats.completion_rate) }}
              ({{ store.checklistStats.done }}/{{ store.checklistStats.total }})
            </p>
            <el-progress
              :percentage="Math.round(store.checklistStats.completion_rate * 100)"
              :stroke-width="10"
            />
            <div class="grid grid-cols-2 gap-2 text-xs mt-3">
              <div><el-tag type="success" size="small">DONE</el-tag> {{ store.checklistStats.done }}</div>
              <div><el-tag type="warning" size="small">진행중</el-tag> {{ store.checklistStats.in_progress }}</div>
              <div><el-tag type="info" size="small">N/A</el-tag> {{ store.checklistStats.na }}</div>
              <div><el-tag type="danger" size="small">차단</el-tag> {{ store.checklistStats.blocked }}</div>
            </div>
          </div>

          <!-- 체크리스트 항목 -->
          <div class="space-y-3">
            <div
              v-for="item in store.checklist"
              :key="item.id"
              class="border rounded p-3"
            >
              <div class="flex items-start justify-between mb-2">
                <div class="flex-1">
                  <p class="text-xs text-gray-500">{{ item.item_code }}</p>
                  <p class="font-medium">{{ item.item_title }}</p>
                </div>
                <el-tag :type="checkStatusType(item.status)" size="small">
                  {{ statusLabel(item.status) }}
                </el-tag>
              </div>
              <p v-if="item.description" class="text-sm text-gray-600 mb-2">
                {{ item.description }}
              </p>
              <el-select
                :model-value="item.status"
                size="small"
                style="width: 100%"
                @update:model-value="(v: CheckStatus) => updateStatus(item, v)"
              >
                <el-option label="시작 안 함" value="NOT_STARTED" />
                <el-option label="진행 중" value="IN_PROGRESS" />
                <el-option label="완료" value="DONE" />
                <el-option label="해당 없음 (N/A)" value="NA" />
                <el-option label="차단됨" value="BLOCKED" />
              </el-select>
            </div>
            <el-empty v-if="store.checklist.length === 0" description="체크리스트가 없습니다" :image-size="80" />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Download } from '@element-plus/icons-vue'
import { useSafetyStore } from '@/stores/safetyStore'
import { useSafeHtml } from '@/composables/useSafeHtml'
import type { CheckStatus, ChecklistItemResult } from '@/api/safety'

const route = useRoute()
const router = useRouter()
const store = useSafetyStore()
const { sanitize } = useSafeHtml()

const reportUuid = computed(() => String(route.params.uuid))
const downloading = ref(false)

async function loadAll(): Promise<void> {
  await store.fetchReport(reportUuid.value)
  await loadChecklist()
}

async function loadChecklist(): Promise<void> {
  await store.fetchChecklist(reportUuid.value)
  try {
    await store.fetchStats(reportUuid.value)
  } catch {
    // 통계 조회 실패 — 무시
  }
}

async function updateStatus(item: ChecklistItemResult, newStatus: CheckStatus): Promise<void> {
  try {
    await store.updateCheckResult(reportUuid.value, item.id, { status: newStatus })
    await store.fetchStats(reportUuid.value)
    ElMessage.success('상태가 업데이트되었습니다')
  } catch {
    ElMessage.error('상태 변경 실패')
  }
}

async function handleDownload(): Promise<void> {
  downloading.value = true
  try {
    const blob = await store.downloadPdf(reportUuid.value)
    const url = window.URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${store.currentReport?.title ?? 'report'}.pdf`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    window.URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('PDF 다운로드 실패')
  } finally {
    downloading.value = false
  }
}

function goMatch(): void {
  router.push({ name: 'safety-match' })
}

function statusType(s?: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'PUBLISHED') return 'success'
  if (s === 'GENERATED') return 'info'
  if (s === 'DRAFT') return 'warning'
  return ''
}

function checkStatusType(s: CheckStatus): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<CheckStatus, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    NOT_STARTED: '',
    IN_PROGRESS: 'warning',
    DONE: 'success',
    NA: 'info',
    BLOCKED: 'danger',
  }
  return map[s] ?? ''
}

function statusLabel(s: CheckStatus): string {
  const map: Record<CheckStatus, string> = {
    NOT_STARTED: '시작 안 함',
    IN_PROGRESS: '진행 중',
    DONE: '완료',
    NA: 'N/A',
    BLOCKED: '차단됨',
  }
  return map[s] ?? s
}

function formatPct(n: number): string {
  return `${(n * 100).toFixed(1)}%`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(() => {
  loadAll()
})
</script>

<style scoped>
.report-body :deep(h1),
.report-body :deep(h2),
.report-body :deep(h3) {
  font-weight: 600;
  margin-top: 1rem;
  margin-bottom: 0.5rem;
}
.report-body :deep(p) {
  margin: 0.5rem 0;
  line-height: 1.6;
}
.report-body :deep(ul),
.report-body :deep(ol) {
  padding-left: 1.5rem;
  margin: 0.5rem 0;
}
.report-body :deep(table) {
  border-collapse: collapse;
  margin: 1rem 0;
}
.report-body :deep(th),
.report-body :deep(td) {
  border: 1px solid #e5e7eb;
  padding: 0.5rem 0.75rem;
}
</style>
