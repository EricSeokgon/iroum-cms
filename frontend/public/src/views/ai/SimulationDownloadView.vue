<!--
  SPEC-CMS-SIM-001 — 공개 시뮬레이션 위저드 (3단계: PDF 다운로드 트리거)
  마운트 시 PDF 생성·다운로드 후 결과 화면으로 리다이렉트.
  비회원 허용. 외부 링크(이메일 등)에서 직접 진입하는 시나리오 지원.
-->
<template>
  <section class="flex flex-col items-center justify-center space-y-4 py-16">
    <LoadingState :message="t('simulation.pdfPreparing')" />
    <p v-if="downloadError" class="text-sm text-red-600">{{ t('simulation.pdfError') }}</p>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { simulationApi } from '@/api/simulationApi'
import LoadingState from '@/components/common/LoadingState.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const sessionId = String(route.params.sessionId ?? '')
const downloadError = ref(false)

async function triggerDownload(): Promise<void> {
  if (!sessionId) {
    router.replace({ name: 'simulation-wizard' })
    return
  }
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
    downloadError.value = true
  } finally {
    // 다운로드 성공·실패와 무관하게 결과 화면으로 안내
    router.replace({ name: 'simulation-result', params: { sessionId } })
  }
}

onMounted(triggerDownload)
</script>
