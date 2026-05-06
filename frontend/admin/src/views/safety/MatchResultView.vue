<template>
  <!-- 매칭 결과 — SPEC-CMS-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">사고사례 매칭 결과</h2>
      <div class="flex gap-2">
        <el-button @click="goProfile">프로필로</el-button>
        <el-button type="primary" :loading="store.matchLoading" @click="runMatch">
          매칭 재실행
        </el-button>
      </div>
    </div>

    <!-- 캐시/만료 정보 -->
    <el-card v-if="store.matchResult" class="mb-4" shadow="never">
      <div class="flex flex-wrap items-center gap-4 text-sm">
        <span>
          <el-tag size="small" :type="store.matchResult.cached ? 'info' : 'success'">
            {{ store.matchResult.cached ? '캐시 결과' : '신규 매칭' }}
          </el-tag>
        </span>
        <span class="text-gray-600">
          생성: {{ formatDate(store.matchResult.generated_at) }}
        </span>
        <span class="text-gray-600">
          만료(TTL): {{ formatDate(store.matchResult.expires_at) }}
        </span>
        <span v-if="store.matchResult.total_score !== undefined" class="text-gray-600">
          종합 점수: {{ formatScore(store.matchResult.total_score) }}
        </span>
        <el-button
          type="success"
          size="small"
          class="ml-auto"
          :loading="generating"
          @click="openTemplateDialog"
        >
          가이드라인 생성
        </el-button>
      </div>
    </el-card>

    <!-- 매칭 결과 카드 -->
    <div v-loading="store.matchLoading">
      <div v-if="!store.matchResult || store.matchResult.matched_incidents.length === 0" class="py-12 text-center text-gray-500">
        매칭 결과가 없습니다. 안전 프로필을 등록한 후 매칭을 실행하세요.
      </div>
      <div v-else class="grid grid-cols-1 md:grid-cols-2 gap-4">
        <el-card
          v-for="m in store.matchResult.matched_incidents"
          :key="m.incident_id"
          shadow="hover"
          class="cursor-pointer"
          @click="goIncident(m.incident_id)"
        >
          <div class="flex items-start justify-between mb-2">
            <div>
              <p class="text-xs text-gray-500">RANK #{{ m.rank }}</p>
              <h3 class="text-base font-semibold">{{ m.incident.incident_type }}</h3>
            </div>
            <el-tag :type="severityType(m.incident.severity)" size="small">
              {{ m.incident.severity }}
            </el-tag>
          </div>
          <p class="text-sm text-gray-700 mb-2 line-clamp-2">{{ m.incident.summary }}</p>
          <div class="flex items-center gap-2 mb-2">
            <span class="text-xs text-gray-500">유사도</span>
            <el-progress
              :percentage="Math.round(m.similarity_score * 100)"
              :stroke-width="8"
              class="flex-1"
            />
            <span class="text-sm font-medium">{{ formatScore(m.similarity_score) }}</span>
          </div>
          <div class="bg-gray-50 rounded p-2 text-xs text-gray-700">
            <p class="font-medium mb-1">매칭 근거 (XAI)</p>
            <p>{{ m.match_reason }}</p>
          </div>
          <div class="mt-2 flex flex-wrap gap-1 text-xs text-gray-500">
            <span>업종: {{ m.incident.industry_code }}</span>
            <span>· 발생: {{ formatShortDate(m.incident.occurred_at) }}</span>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 가이드라인 생성 다이얼로그 -->
    <el-dialog v-model="dialogOpen" title="가이드라인 보고서 생성" width="500px">
      <el-form label-width="120px">
        <el-form-item label="템플릿">
          <el-select v-model="selectedTemplateCode" placeholder="템플릿 선택" style="width: 100%">
            <el-option
              v-for="t in store.templates"
              :key="t.code"
              :label="`${t.name} (v${t.version})`"
              :value="t.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="제목 (선택)">
          <el-input v-model="customTitle" placeholder="기본값: 템플릿명" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">취소</el-button>
        <el-button type="primary" :loading="generating" :disabled="!selectedTemplateCode" @click="generateReport">
          생성
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useSafetyStore } from '@/stores/safetyStore'
import type { IncidentSeverity } from '@/api/safety'

const router = useRouter()
const store = useSafetyStore()

const dialogOpen = ref(false)
const generating = ref(false)
const selectedTemplateCode = ref('')
const customTitle = ref('')

async function runMatch(): Promise<void> {
  try {
    await store.runMatch()
    ElMessage.success('매칭이 완료되었습니다')
  } catch {
    ElMessage.error('매칭 실행 실패')
  }
}

function goProfile(): void {
  router.push({ name: 'safety-profile' })
}

function goIncident(id: number): void {
  router.push({ name: 'safety-incident-detail', params: { id } })
}

async function openTemplateDialog(): Promise<void> {
  if (store.templates.length === 0) await store.fetchTemplates()
  dialogOpen.value = true
}

async function generateReport(): Promise<void> {
  generating.value = true
  try {
    const report = await store.createReport({
      template_code: selectedTemplateCode.value,
      custom_title: customTitle.value || undefined,
    })
    ElMessage.success('가이드라인 보고서가 생성되었습니다')
    dialogOpen.value = false
    router.push({ name: 'safety-report-detail', params: { uuid: report.uuid } })
  } catch {
    ElMessage.error('보고서 생성 실패')
  } finally {
    generating.value = false
  }
}

function severityType(s: IncidentSeverity): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<IncidentSeverity, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    LOW: 'info',
    MEDIUM: '',
    HIGH: 'warning',
    CRITICAL: 'danger',
  }
  return map[s] ?? ''
}

function formatScore(n: number): string {
  return `${(n * 100).toFixed(1)}%`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

function formatShortDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR')
}

onMounted(async () => {
  // 캐시된 결과 우선 시도
  try {
    await store.fetchCachedMatch()
  } catch {
    // 캐시 없음 — 무시
  }
})
</script>
