<template>
  <!-- 수정 이력 패널 — SPEC-CMS-CONTENT-REVISION-001 M4 -->
  <div>
    <h3 class="mb-3 text-base font-medium text-gray-800">{{ t('revision.history') }}</h3>

    <!-- 이력 없음 -->
    <el-empty
      v-if="!loading && entries.length === 0"
      :description="t('revision.noHistory')"
      :image-size="80"
    />

    <template v-else>
      <el-table
        v-loading="loading"
        :data="entries"
        border
        size="small"
        :aria-label="t('revision.history')"
      >
        <el-table-column :label="t('revision.history')" width="110">
          <template #default="{ row }">
            {{ t('revision.version', { n: row.version }) }}
          </template>
        </el-table-column>
        <el-table-column :label="t('common.createdAt')" width="180">
          <template #default="{ row }">{{ formatDate(row.editedAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" min-width="220">
          <template #default="{ row }">
            <div class="flex gap-2">
              <el-button
                size="small"
                plain
                :disabled="row.version <= 1"
                @click="loadDiff(row.version)"
              >
                {{ t('revision.viewDiff') }}
              </el-button>
              <el-button
                size="small"
                type="warning"
                plain
                @click="emitRollback(row.version)"
              >
                {{ t('revision.rollback') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 선택한 버전 diff -->
      <div v-if="activeDiffVersion !== null" class="mt-4">
        <div class="mb-2 text-sm text-gray-500">
          v{{ activeDiffVersion - 1 }} → v{{ activeDiffVersion }}
        </div>
        <DiffViewer v-loading="diffLoading" :diffs="diffs" />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { boardApi } from '@/api/board'
import { pages } from '@/api/content'
import DiffViewer from '@/components/revision/DiffViewer.vue'
import type { RevisionDiffResponse, RevisionHistoryEntry } from '@/types/revision'

const props = defineProps<{
  entityType: 'post' | 'page'
  entityId: number
  currentVersion: number
}>()

const emit = defineEmits<{
  (e: 'rollback', version: number): void
}>()

const { t } = useI18n()

const entries = ref<RevisionHistoryEntry[]>([])
const loading = ref(false)
const diffs = ref<RevisionDiffResponse[]>([])
const diffLoading = ref(false)
const activeDiffVersion = ref<number | null>(null)

// 엔티티 유형별 이력 목록 로드 후 통합 형태로 정규화
async function loadHistory(): Promise<void> {
  loading.value = true
  try {
    if (props.entityType === 'post') {
      const res = await boardApi.getPostHistory(props.entityId, 0, 100)
      entries.value = res.data.content.map((h) => ({
        version: h.version,
        editedAt: h.editedAt,
        editorName: h.editorName,
        summary: h.editReason,
      }))
    } else {
      const res = await pages.history(props.entityId)
      entries.value = res.data.map((h) => ({
        version: h.version,
        editedAt: h.editedAt,
        summary: h.changeSummary,
      }))
    }
  } catch {
    ElMessage.error(t('revision.noHistory'))
  } finally {
    loading.value = false
  }
}

// 이전 버전(version-1)과 선택 버전(version)의 diff 로드
async function loadDiff(version: number): Promise<void> {
  if (version <= 1) return
  diffLoading.value = true
  activeDiffVersion.value = version
  try {
    const res =
      props.entityType === 'post'
        ? await boardApi.getPostHistoryDiff(props.entityId, version - 1, version)
        : await pages.historyDiff(props.entityId, version - 1, version)
    diffs.value = res.data
  } catch {
    ElMessage.error(t('revision.noHistory'))
    diffs.value = []
  } finally {
    diffLoading.value = false
  }
}

function emitRollback(version: number): void {
  emit('rollback', version)
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

onMounted(loadHistory)
</script>
