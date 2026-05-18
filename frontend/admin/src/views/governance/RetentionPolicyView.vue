<template>
  <!-- 보존 정책 관리 — SPEC-CMS-009 REQ-GOV-006~009 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">데이터 보존 정책</h2>
      <div class="flex gap-2">
        <el-button :icon="Refresh" @click="reload">새로고침</el-button>
        <el-button v-if="isAdmin" type="primary" :icon="Plus" @click="openCreate">신규 등록</el-button>
      </div>
    </div>

    <!-- 정책별 처리 건수 차트 -->
    <el-card class="mb-4" shadow="never">
      <template #header>
        <span class="text-sm font-semibold">정책별 누적 처리 건수</span>
      </template>
      <div v-if="!hasChartData" class="py-8 text-center text-gray-400 text-sm">
        실행 이력이 없습니다
      </div>
      <VChart
        v-else
        :option="chartOption"
        autoresize
        style="height: 240px"
      />
    </el-card>

    <!-- 테이블 -->
    <el-card shadow="never" v-loading="store.retentionLoading">
      <el-table :data="store.retentionPolicies" stripe empty-text="등록된 보존 정책이 없습니다">
        <el-table-column prop="target_table" label="대상 테이블" min-width="160" />
        <el-table-column prop="policy_type" label="유형" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="row.policy_type === 'ARCHIVE' ? 'warning' : 'danger'">
              {{ row.policy_type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="retention_months" label="보존 개월" width="110" align="center" />
        <el-table-column prop="archive_table" label="아카이브 테이블" min-width="160">
          <template #default="{ row }">{{ row.archive_table || '-' }}</template>
        </el-table-column>
        <el-table-column prop="schedule_cron" label="실행 스케줄" min-width="180">
          <template #default="{ row }">
            <code class="text-xs">{{ row.schedule_cron }}</code>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="상태" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="last_run_at" label="마지막 실행" width="160">
          <template #default="{ row }">
            {{ row.last_run_at ? formatDateTime(row.last_run_at) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="작업" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button v-if="isAdmin" size="small" link type="primary" @click="openEdit(row)">수정</el-button>
            <el-button v-if="isAdmin" size="small" link type="warning" @click="handleRunNow(row)">지금 실행</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="대상 테이블" prop="target_table">
          <el-input v-model="form.target_table" :disabled="editMode" placeholder="예: tb_audit_log" />
        </el-form-item>
        <el-form-item label="정책 유형" prop="policy_type">
          <el-radio-group v-model="form.policy_type">
            <el-radio value="ARCHIVE">ARCHIVE (아카이브)</el-radio>
            <el-radio value="DELETE">DELETE (삭제)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="보존 개월" prop="retention_months">
          <el-input-number v-model="form.retention_months" :min="1" :max="120" />
        </el-form-item>
        <el-form-item v-if="form.policy_type === 'ARCHIVE'" label="아카이브 테이블" prop="archive_table">
          <el-input v-model="form.archive_table" placeholder="예: tb_audit_log_archive" />
        </el-form-item>
        <el-form-item label="실행 스케줄" prop="schedule_cron">
          <el-input v-model="form.schedule_cron" placeholder="0 0 2 * * ?" />
          <p class="mt-1 text-xs text-gray-400">Spring 6-필드 cron 형식 (초 분 시 일 월 요일)</p>
        </el-form-item>
        <el-form-item label="상태" prop="status">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="PAUSED" value="PAUSED" />
            <el-option label="INACTIVE" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">취소</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">저장</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent } from 'echarts/components'
import { useGovernanceStore } from '@/stores/governanceStore'
import { useAuthStore } from '@/stores/auth'
import type { RetentionPolicy, RetentionPolicyRequest, RetentionStatus } from '@/api/governance'

// @MX:NOTE: [AUTO] vue-echarts 컴포넌트 등록 — BarChart 1종 사용
use([CanvasRenderer, BarChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent])

const store = useGovernanceStore()
const authStore = useAuthStore()
const isAdmin = computed(() =>
  (authStore.user?.roleCodes ?? []).some(r => r === 'SUPER_ADMIN' || r === 'ADMIN'),
)

// Cron 6-field validator: 6 토큰
const cronRegex = /^\s*\S+\s+\S+\s+\S+\s+\S+\s+\S+\s+\S+\s*$/

async function reload(): Promise<void> {
  await store.fetchRetentionPolicies()
}

// 차트 데이터
const hasChartData = computed(() =>
  store.retentionPolicies.some(p => p.last_run_at),
)

interface ChartOption {
  [key: string]: unknown
  tooltip?: Record<string, unknown>
  legend?: Record<string, unknown>
  grid?: Record<string, unknown>
  xAxis?: Record<string, unknown>
  yAxis?: Record<string, unknown>
  series?: unknown[]
}

const chartOption = computed<ChartOption>(() => {
  const policies = store.retentionPolicies.filter(p => p.last_run_at)
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0 },
    grid: { left: 50, right: 16, top: 16, bottom: 36 },
    xAxis: {
      type: 'category',
      data: policies.map(p => p.target_table),
      axisLabel: { interval: 0, rotate: 25, fontSize: 10 },
    },
    yAxis: { type: 'value' },
    series: [
      {
        name: '정책 유형',
        type: 'bar',
        data: policies.map(p => ({
          value: p.retention_months,
          itemStyle: { color: p.policy_type === 'ARCHIVE' ? '#ed6c02' : '#d32f2f' },
        })),
      },
    ],
  }
})

// 등록/수정
const dialogVisible = ref(false)
const editMode = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const dialogTitle = computed(() => (editMode.value ? '보존 정책 수정' : '보존 정책 등록'))

const form = reactive<RetentionPolicyRequest>({
  target_table: '',
  policy_type: 'ARCHIVE',
  retention_months: 12,
  archive_table: undefined,
  schedule_cron: '0 0 2 * * ?',
  status: 'ACTIVE',
})

const rules: FormRules = {
  target_table: [{ required: true, message: '대상 테이블을 입력하세요', trigger: 'blur' }],
  policy_type: [{ required: true, message: '정책 유형을 선택하세요', trigger: 'change' }],
  retention_months: [{ required: true, message: '보존 개월을 입력하세요', trigger: 'blur' }],
  archive_table: [
    {
      validator: (_rule, value: string | undefined, callback) => {
        if (form.policy_type === 'ARCHIVE' && (!value || !value.trim())) {
          callback(new Error('ARCHIVE 정책은 아카이브 테이블이 필요합니다'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  schedule_cron: [
    { required: true, message: 'cron 식을 입력하세요', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (!value || !cronRegex.test(value)) {
          callback(new Error('Spring 6-필드 cron 형식이어야 합니다'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  status: [{ required: true, message: '상태를 선택하세요', trigger: 'change' }],
}

function resetForm(): void {
  form.target_table = ''
  form.policy_type = 'ARCHIVE'
  form.retention_months = 12
  form.archive_table = undefined
  form.schedule_cron = '0 0 2 * * ?'
  form.status = 'ACTIVE'
}

function openCreate(): void {
  editMode.value = false
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: RetentionPolicy): void {
  editMode.value = true
  editingId.value = row.id
  form.target_table = row.target_table
  form.policy_type = row.policy_type
  form.retention_months = row.retention_months
  form.archive_table = row.archive_table
  form.schedule_cron = row.schedule_cron
  form.status = row.status
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const payload: RetentionPolicyRequest = {
      ...form,
      archive_table: form.policy_type === 'ARCHIVE' ? form.archive_table : undefined,
    }
    if (editMode.value && editingId.value) {
      await store.updateRetentionPolicy(editingId.value, payload)
      ElMessage.success('수정되었습니다')
    } else {
      await store.createRetentionPolicy(payload)
      ElMessage.success('등록되었습니다')
    }
    dialogVisible.value = false
    await reload()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '저장 실패')
  } finally {
    submitting.value = false
  }
}

async function handleRunNow(row: RetentionPolicy): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `"${row.target_table}" 정책을 즉시 실행하시겠습니까?`,
      '실행 확인',
      { confirmButtonText: '실행', cancelButtonText: '취소', type: 'warning' },
    )
    const res = await store.runRetentionPolicy(row.id)
    ElMessage.success(`실행 시작 (배치 로그 ID: ${res.batch_log_id})`)
    await reload()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e instanceof Error ? e.message : '실행 실패')
  }
}

// 헬퍼
function statusTagType(s: RetentionStatus): '' | 'success' | 'warning' | 'info' {
  if (s === 'ACTIVE') return 'success'
  if (s === 'PAUSED') return 'warning'
  return 'info'
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

onMounted(() => {
  reload()
})
</script>
