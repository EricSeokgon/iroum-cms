<template>
  <el-dialog
    v-model="visible"
    :title="t('organizations.action.viewHistory') + ` — ${orgName}`"
    width="680px"
    :close-on-click-modal="true"
    :aria-label="t('organizations.action.viewHistory')"
    @close="emit('close')"
  >
    <div v-if="loading" class="flex justify-center py-8">
      <el-icon class="animate-spin text-2xl text-blue-500"><i-ep-loading /></el-icon>
    </div>

    <div v-else-if="entries.length === 0" class="py-8 text-center text-gray-400">
      {{ t('organizations.history.empty') }}
    </div>

    <div v-else class="flex gap-4" style="min-height: 320px;">
      <!-- 좌측: 버전 목록 -->
      <div class="w-48 overflow-y-auto border-r border-gray-200 pr-3">
        <ul role="listbox" :aria-label="t('organizations.history.versionList')">
          <li
            v-for="entry in entries"
            :key="entry.id"
            role="option"
            :aria-selected="selectedEntry?.id === entry.id"
            class="mb-1 cursor-pointer rounded px-2 py-2 text-sm transition-colors"
            :class="{
              'bg-blue-50 text-blue-700 font-semibold': selectedEntry?.id === entry.id,
              'text-gray-600 hover:bg-gray-50': selectedEntry?.id !== entry.id,
            }"
            :tabindex="0"
            @click="selectedEntry = entry"
            @keydown.enter="selectedEntry = entry"
            @keydown.space.prevent="selectedEntry = entry"
          >
            <div class="font-medium">v{{ entry.version }}</div>
            <div class="text-xs text-gray-400">{{ formatDate(entry.changedAt) }}</div>
          </li>
        </ul>
      </div>

      <!-- 우측: 선택된 버전 스냅샷 -->
      <div class="flex-1 overflow-auto">
        <div v-if="selectedEntry">
          <div class="mb-3 flex flex-wrap gap-4 text-sm text-gray-600">
            <span v-if="selectedEntry.changeSummary">
              {{ t('organizations.history.summary') }}: {{ selectedEntry.changeSummary }}
            </span>
            <span v-if="selectedEntry.changedBy">
              {{ t('organizations.history.changedBy') }}: {{ selectedEntry.changedBy }}
            </span>
            <span>{{ t('organizations.history.changedAt') }}: {{ formatDate(selectedEntry.changedAt) }}</span>
          </div>
          <el-input
            :model-value="prettySnapshot(selectedEntry.snapshot)"
            type="textarea"
            :rows="12"
            readonly
            resize="none"
            :aria-label="t('organizations.history.snapshot')"
            class="font-mono text-xs"
          />
        </div>
        <div v-else class="flex h-full items-center justify-center text-gray-400">
          {{ t('organizations.history.selectVersion') }}
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="emit('close')">{{ t('common.cancel') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { organizationsApi } from '@/api/organizations'
import type { OrganizationHistoryEntry } from '@iroum/shared/types/api'

const props = defineProps<{
  orgId: number
  orgName: string
}>()

const emit = defineEmits<{
  close: []
}>()

const { t } = useI18n()

const visible = ref(true)
const loading = ref(false)
const entries = ref<OrganizationHistoryEntry[]>([])
const selectedEntry = ref<OrganizationHistoryEntry | null>(null)

onMounted(async () => {
  loading.value = true
  try {
    const res = await organizationsApi.history(props.orgId)
    // 버전 내림차순 정렬
    entries.value = [...res.data].sort((a, b) => b.version - a.version)
    if (entries.value.length > 0) {
      selectedEntry.value = entries.value[0]
    }
  } catch {
    ElMessage.error(t('organizations.error.historyFailed'))
  } finally {
    loading.value = false
  }
})

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

function prettySnapshot(snapshot: Record<string, unknown>): string {
  try {
    return JSON.stringify(snapshot, null, 2)
  } catch {
    return String(snapshot)
  }
}
</script>
