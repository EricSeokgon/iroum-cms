<template>
  <!-- 수신 동의 관리 — SPEC-CMS-007 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">알림 수신 동의 관리</h2>
      <el-button type="primary" :loading="store.preferencesLoading" @click="handleSave">
        저장
      </el-button>
    </div>

    <el-card v-loading="store.preferencesLoading" shadow="never">
      <p class="text-sm text-gray-600 mb-4">
        채널별, 카테고리별로 수신 여부를 설정할 수 있습니다. 설정은 즉시 저장 버튼 클릭 시 반영됩니다.
      </p>

      <el-table :data="rows" border stripe>
        <el-table-column label="카테고리" prop="categoryLabel" width="220" fixed />
        <el-table-column
          v-for="ch in channels"
          :key="ch.value"
          :label="ch.label"
          align="center"
          min-width="140"
        >
          <template #default="{ row }">
            <el-switch v-model="matrix[row.category][ch.value]" />
          </template>
        </el-table-column>
      </el-table>

      <div class="mt-4 text-xs text-gray-500">
        <p>· KAKAO: 카카오 알림톡 (영업시간 외 발송 불가)</p>
        <p>· EMAIL: 이메일 발송</p>
        <p>· INAPP: 사이트 내 알림</p>
        <p class="mt-2">
          MARKETING 카테고리는 광고성 정보로, 별도 동의가 필요합니다 (정보통신망법 제50조).
        </p>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { usePolicyStore } from '@/stores/policyStore'
import type { Channel, DispatchType, NotificationPreference } from '@/api/policy'

const store = usePolicyStore()

const channels: Array<{ value: Channel; label: string }> = [
  { value: 'KAKAO', label: '카카오 알림톡' },
  { value: 'EMAIL', label: '이메일' },
  { value: 'INAPP', label: '사이트 알림' },
]

const categories: Array<{ value: DispatchType; label: string }> = [
  { value: 'POLICY_MATCH', label: '정책 매칭 결과' },
  { value: 'ANNOUNCEMENT', label: '공지사항' },
  { value: 'REMINDER', label: '신청 마감 리마인더' },
  { value: 'MARKETING', label: '마케팅·이벤트' },
]

// 매트릭스: matrix[category][channel] = boolean
type Matrix = Record<DispatchType, Record<Channel, boolean>>
const matrix = reactive<Matrix>({
  POLICY_MATCH: { KAKAO: false, EMAIL: false, INAPP: false },
  ANNOUNCEMENT: { KAKAO: false, EMAIL: false, INAPP: false },
  REMINDER: { KAKAO: false, EMAIL: false, INAPP: false },
  MARKETING: { KAKAO: false, EMAIL: false, INAPP: false },
})

const rows = computed(() =>
  categories.map(c => ({
    category: c.value,
    categoryLabel: c.label,
  })),
)

function buildPayload(): NotificationPreference[] {
  const list: NotificationPreference[] = []
  for (const cat of categories) {
    for (const ch of channels) {
      list.push({
        channel: ch.value,
        category: cat.value,
        opted_in: matrix[cat.value][ch.value],
      })
    }
  }
  return list
}

async function handleSave(): Promise<void> {
  try {
    await store.updateSubscriptions({ preferences: buildPayload() })
    ElMessage.success('수신 동의가 저장되었습니다')
  } catch {
    ElMessage.error('수신 동의 저장 실패')
  }
}

onMounted(async () => {
  try {
    await store.fetchMySubscriptions()
    const prefs = store.myPreferences?.preferences ?? []
    for (const p of prefs) {
      if (matrix[p.category] && p.channel in matrix[p.category]) {
        matrix[p.category][p.channel] = p.opted_in
      }
    }
  } catch {
    // 첫 진입 시 비어 있을 수 있음 — 무시
  }
})
</script>
