<!--
  SPEC-CMS-PUBLIC-001 T-009 — 공개 통계 (D-05)
  - statsApi.widget('public-stats') 로 위젯 데이터 로드
  - KpiChart 컴포넌트로 차트 + 데이터 테이블 fallback 렌더링
  - Single-widget 또는 multi-widget 지원
-->
<template>
  <section class="space-y-6">
    <header>
      <h1 class="text-2xl font-bold text-content-DEFAULT">{{ t('stats.title') }}</h1>
      <p class="mt-1 text-sm text-content-muted">{{ t('stats.subtitle') }}</p>
    </header>

    <LoadingState v-if="loading" :rows="3" />
    <ErrorState v-else-if="error" @retry="loadStats" />
    <div
      v-else-if="widgets.length > 0"
      class="grid grid-cols-1 gap-6 md:grid-cols-2"
      data-testid="stats-grid"
    >
      <KpiChart v-for="w in widgets" :key="w.code" :widget="w" />
    </div>
    <EmptyState v-else :message="t('stats.noData')" />
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { statsApi, type WidgetData } from '@/api/statsApi'
import KpiChart from '@/components/stats/KpiChart.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const { t } = useI18n()
const widgets = ref<WidgetData[]>([])
const loading = ref(false)
const error = ref(false)

// @MX:NOTE: [AUTO] public-stats widget 은 단일 또는 다중 위젯 응답 모두 허용
async function loadStats(): Promise<void> {
  loading.value = true
  error.value = false
  try {
    const res = await statsApi.widget('public-stats')
    // 위젯이 배열을 포함하는 경우 (data: WidgetData[]) 또는 단일 위젯
    if (Array.isArray(res.data) && res.data.every((d) => d && typeof d === 'object' && 'code' in d)) {
      widgets.value = res.data as WidgetData[]
    } else {
      widgets.value = [res]
    }
  } catch {
    error.value = true
    widgets.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>
