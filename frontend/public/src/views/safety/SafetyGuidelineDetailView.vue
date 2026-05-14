<!--
  SPEC-CMS-PUBLIC-001 T-007 — 안전 가이드 상세
  AC: C-05 (체크리스트 렌더링 + 인쇄 버튼)
-->
<template>
  <section class="space-y-6">
    <LoadingState v-if="loading" />
    <ErrorState v-else-if="error" @retry="loadDetail" />
    <article v-else-if="guideline" class="space-y-6">
      <header class="flex flex-wrap items-center justify-between gap-3 border-b border-gray-200 pb-4">
        <div>
          <h1 class="text-2xl font-bold text-content-DEFAULT">{{ guideline.title }}</h1>
          <dl class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-sm text-content-muted">
            <div class="flex items-center gap-1">
              <dt>{{ t('safety.industryCode') }}:</dt>
              <dd>{{ guideline.industryCode }}</dd>
            </div>
            <div v-if="guideline.processCode" class="flex items-center gap-1">
              <dt>{{ t('safety.processCode') }}:</dt>
              <dd>{{ guideline.processCode }}</dd>
            </div>
            <div class="flex items-center gap-1">
              <dt>{{ t('safety.lastUpdated') }}:</dt>
              <dd>{{ guideline.updatedAt.slice(0, 10) }}</dd>
            </div>
          </dl>
        </div>
        <button
          type="button"
          class="rounded-md border border-gray-300 px-4 py-2 text-sm hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-primary-600"
          data-testid="safety-print-button"
          @click="onPrint"
        >
          {{ t('safety.printGuide') }}
        </button>
      </header>

      <section :aria-label="t('policy.description')">
        <NoticeContent :html="guideline.descriptionHtml" />
      </section>

      <section v-if="guideline.checklist.length > 0" :aria-label="t('safety.checklist')">
        <h2 class="mb-3 text-lg font-bold text-content-DEFAULT">{{ t('safety.checklist') }}</h2>
        <SafetyChecklist :items="guideline.checklist" />
      </section>
    </article>
  </section>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { safetyApi, type SafetyGuidelineDetail } from '@/api/safetyApi'
import NoticeContent from '@/components/notice/NoticeContent.vue'
import SafetyChecklist from '@/components/safety/SafetyChecklist.vue'
import LoadingState from '@/components/common/LoadingState.vue'
import ErrorState from '@/components/common/ErrorState.vue'

const { t } = useI18n()
const route = useRoute()

const guideline = ref<SafetyGuidelineDetail | null>(null)
const loading = ref(false)
const error = ref(false)

async function loadDetail(): Promise<void> {
  const id = Number(route.params.id)
  if (!Number.isFinite(id)) {
    error.value = true
    return
  }
  loading.value = true
  error.value = false
  try {
    guideline.value = await safetyApi.guideline(id)
  } catch {
    error.value = true
    guideline.value = null
  } finally {
    loading.value = false
  }
}

function onPrint(): void {
  window.print()
}

watch(() => route.params.id, loadDetail)

onMounted(() => {
  loadDetail()
})
</script>
