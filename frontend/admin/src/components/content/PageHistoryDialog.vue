<template>
  <!-- 페이지 이력 비교/롤백 다이얼로그 — SPEC-CMS-004 REQ-CONTENT-005-D-6/7 -->
  <el-dialog
    v-model="visible"
    :title="t('content.page.history.title')"
    width="800px"
    :close-on-click-modal="false"
  >
    <div v-loading="loading">
      <!-- 이력 목록 -->
      <el-table
        :data="histories"
        stripe
        size="small"
        :aria-label="t('content.page.history.title')"
        @selection-change="onSelectionChange"
      >
        <caption class="sr-only">{{ t('content.page.history.title') }}</caption>
        <el-table-column type="selection" width="40" />
        <el-table-column prop="version" :label="t('content.page.history.version')" width="70" />
        <el-table-column prop="editedAt" :label="t('content.page.history.editedAt')" width="180">
          <template #default="{ row }">{{ formatDate(row.editedAt) }}</template>
        </el-table-column>
        <el-table-column prop="changeSummary" :label="t('content.page.history.summary')" />
        <el-table-column :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <el-popconfirm
              :title="t('content.page.history.rollbackConfirm', { v: row.version })"
              @confirm="rollback(row.version)"
            >
              <template #reference>
                <el-button size="small" type="warning" plain>
                  {{ t('content.page.history.rollback') }}
                </el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <!-- 2개 선택 시 비교 패널 -->
      <div v-if="compareVersions.length === 2" class="mt-4">
        <div class="mb-2 flex items-center gap-2 text-sm text-blue-600">
          <el-icon><i-ep-view /></el-icon>
          {{ t('content.page.history.comparing', { a: compareVersions[0], b: compareVersions[1] }) }}
        </div>
        <JsonDiffPanel :left="getSnapshot(compareVersions[0])" :right="getSnapshot(compareVersions[1])" />
      </div>
    </div>

    <template #footer>
      <div class="flex items-center gap-2">
        <span v-if="compareVersions.length === 2" class="text-xs text-gray-500">
          {{ t('content.page.history.selectTwo') }}
        </span>
        <el-button @click="visible = false">{{ t('common.close') }}</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { pages } from '@/api/content'
import type { PageHistoryResponse } from '@/api/content'
import JsonDiffPanel from '@/components/content/JsonDiffPanel.vue'

const { t } = useI18n()

const props = defineProps<{
  modelValue: boolean
  pageId: number | null
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
  (e: 'rolledBack'): void
}>()

const visible = ref(props.modelValue)
const histories = ref<PageHistoryResponse[]>([])
const loading = ref(false)
const compareVersions = ref<number[]>([])

watch(() => props.modelValue, (val) => { visible.value = val })
watch(visible, (val) => emit('update:modelValue', val))

watch([visible, () => props.pageId], async ([show, id]) => {
  if (show && id) {
    await loadHistory(id)
  }
})

async function loadHistory(id: number): Promise<void> {
  loading.value = true
  try {
    const res = await pages.history(id)
    histories.value = res.data
  } catch {
    ElMessage.error(t('content.page.history.loadError'))
  } finally {
    loading.value = false
  }
}

function onSelectionChange(rows: PageHistoryResponse[]): void {
  compareVersions.value = rows.slice(0, 2).map(r => r.version)
}

function getSnapshot(version: number): Record<string, unknown> {
  return histories.value.find(h => h.version === version)?.snapshot ?? {}
}

async function rollback(version: number): Promise<void> {
  if (!props.pageId) return
  try {
    await pages.rollback(props.pageId, version)
    ElMessage.success(t('content.page.history.rolledBack', { v: version }))
    emit('rolledBack')
    visible.value = false
  } catch {
    ElMessage.error(t('content.page.history.rollbackError'))
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString()
}
</script>
