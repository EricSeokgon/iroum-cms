<template>
  <div class="mx-auto max-w-2xl py-8">
    <!-- 페이지 제목 — KWCAG 2.4.6 제목과 레이블 -->
    <h2 class="mb-6 text-xl font-semibold text-gray-900">
      {{ t('notifications.title') }}
    </h2>

    <!-- 알림 설정 카드 -->
    <el-card shadow="never">
      <div class="flex items-start justify-between gap-6">
        <div class="flex-1">
          <label
            id="qna-answer-email-label"
            class="block text-sm font-medium text-gray-900"
          >
            {{ t('notifications.qnaAnswerEmail') }}
          </label>
          <p class="mt-1 text-xs text-gray-500">
            {{ t('notifications.qnaAnswerEmailDesc') }}
          </p>
        </div>

        <!-- el-switch — 불리언 토글 (체크박스 대신 사용) -->
        <el-switch
          v-model="qnaAnswerEmail"
          aria-labelledby="qna-answer-email-label"
          data-testid="switch-qna-answer-email"
          @change="onToggleQnaAnswerEmail"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
// @MX:NOTE: [AUTO] Q&A 답변 알림 이메일 수신 설정 — 현재 설정 GET 엔드포인트 미구현으로 초기값 true 가정

import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { meApi } from '@/api/me'

const { t } = useI18n()

// 현재 설정 GET 엔드포인트가 없으므로 초기값 true 가정
const qnaAnswerEmail = ref<boolean>(true)

/**
 * Q&A 답변 이메일 알림 설정 토글 핸들러
 * 실패 시 이전 상태로 복구하고 에러 메시지 표시
 */
async function onToggleQnaAnswerEmail(value: string | number | boolean): Promise<void> {
  const next = Boolean(value)
  const previous = !next
  try {
    await meApi.updateQnaNotificationPreference(next)
    ElMessage.success(t('notifications.saved'))
  } catch {
    // 실패 시 스위치 상태 복구
    qnaAnswerEmail.value = previous
    ElMessage.error(t('common.error.network'))
  }
}
</script>
