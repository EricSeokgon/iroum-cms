<!--
  SPEC-CMS-SIM-001 — 공개 창업 시뮬레이션 위저드 (2단계: 결과)
  비회원 허용. sessionId 라우트 파라미터로 결과 조회.
  projectionResult JSON({ projection: [...] }) → 표 렌더, recommendedPolicies → 목록.
-->
<template>
  <section class="space-y-6">
    <header class="flex items-center justify-between">
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('simulation.resultTitle') }}</h1>
      <router-link
        :to="{ name: 'simulation-wizard' }"
        class="text-sm text-blue-700 hover:underline"
      >
        {{ t('simulation.restart') }}
      </router-link>
    </header>

    <LoadingState v-if="store.loading" />
    <ErrorState v-else-if="store.error" @retry="load" />

    <div v-else-if="store.currentResult" data-testid="simulation-result" class="space-y-6">
      <!-- 적용 시나리오 -->
      <p class="text-sm text-content-muted">
        {{ t('simulation.horizonApplied', { years: store.currentResult.horizonApplied }) }}
      </p>

      <!-- 매출 투영 표 -->
      <section class="space-y-2">
        <h2 class="text-lg font-semibold text-content-DEFAULT">
          {{ t('simulation.projectionTitle') }}
        </h2>
        <table
          v-if="projectionRows.length"
          data-testid="simulation-projection-table"
          class="w-full border-collapse text-sm"
        >
          <thead>
            <tr class="border-b border-gray-200 text-left text-content-muted">
              <th class="py-2 pr-4 font-medium">{{ t('simulation.colYear') }}</th>
              <th class="py-2 pr-4 font-medium">{{ t('simulation.colRevenue') }}</th>
              <th class="py-2 font-medium">{{ t('simulation.colGrowth') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(row, idx) in projectionRows"
              :key="idx"
              class="border-b border-gray-100"
            >
              <td class="py-2 pr-4">{{ row.year }}</td>
              <td class="py-2 pr-4">{{ formatAmount(row.revenue) }}</td>
              <td class="py-2">{{ formatGrowth(row.growth) }}</td>
            </tr>
          </tbody>
        </table>
        <EmptyState v-else :message="t('simulation.projectionEmpty')" />
      </section>

      <!-- 추천 정책 -->
      <section class="space-y-2">
        <h2 class="text-lg font-semibold text-content-DEFAULT">
          {{ t('simulation.policiesTitle') }}
        </h2>
        <ul
          v-if="policyItems.length"
          data-testid="simulation-policies"
          class="list-inside list-disc space-y-1 text-sm text-gray-800"
        >
          <li v-for="(name, idx) in policyItems" :key="idx">{{ name }}</li>
        </ul>
        <p v-else data-testid="simulation-policies-placeholder" class="text-sm text-content-muted">
          {{ t('simulation.policiesPlaceholder') }}
        </p>
      </section>

      <!-- PDF 다운로드 -->
      <div class="space-y-1">
        <button
          type="button"
          data-testid="simulation-pdf-btn"
          :disabled="pdfDownloading"
          class="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-60"
          @click="downloadPdf"
        >
          {{ pdfDownloading ? t('simulation.pdfDownloading') : t('simulation.pdfDownload') }}
        </button>
        <p v-if="pdfError" class="text-sm text-red-600">{{ t('simulation.pdfError') }}</p>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { simulationApi } from '@/api/simulationApi'
import { useSimulationStore } from '@/stores/simulationStore'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const route = useRoute()
const store = useSimulationStore()

const sessionId = String(route.params.sessionId ?? '')
const pdfDownloading = ref(false)
const pdfError = ref(false)

// projectionResult JSON 파싱 → 표 행 배열 (방어적 파싱)
interface ProjectionRow {
  year: number | string
  revenue: number
  growth: number
}

const projectionRows = computed<ProjectionRow[]>(() => {
  const raw = store.currentResult?.projectionResult
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as { projection?: ProjectionRow[] }
    return Array.isArray(parsed.projection) ? parsed.projection : []
  } catch {
    return []
  }
})

// recommendedPolicies JSON 파싱 → 정책명 목록 (null/빈 값 시 빈 배열)
const policyItems = computed<string[]>(() => {
  const raw = store.currentResult?.recommendedPolicies
  if (!raw) return []
  try {
    const parsed = JSON.parse(raw) as unknown
    if (Array.isArray(parsed)) {
      return parsed.map((p) =>
        typeof p === 'string' ? p : ((p as { name?: string })?.name ?? String(p)),
      )
    }
    return []
  } catch {
    return []
  }
})

function formatAmount(value: number): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '-'
  return `${value.toLocaleString()}${t('simulation.won')}`
}

function formatGrowth(value: number): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return '-'
  return `${(value * 100).toFixed(1)}%`
}

async function downloadPdf(): Promise<void> {
  if (!sessionId) return
  pdfDownloading.value = true
  pdfError.value = false
  try {
    const blob = await simulationApi.generatePdf(sessionId)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `simulation-${sessionId}.pdf`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)
  } catch {
    pdfError.value = true
  } finally {
    pdfDownloading.value = false
  }
}

async function load(): Promise<void> {
  if (!sessionId) return
  // 위저드에서 직접 진입한 경우 store에 이미 결과가 있으면 재조회 생략
  if (store.currentResult?.sessionId === sessionId) return
  try {
    await store.loadResult(sessionId)
  } catch {
    // store.error로 ErrorState 표시
  }
}

onMounted(load)

defineExpose({ projectionRows, policyItems, downloadPdf, load })
</script>
