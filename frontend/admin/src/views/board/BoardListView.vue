<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('board.masters.title') }}</h2>
      <el-button
        v-if="isSuperAdmin"
        type="primary"
        :aria-label="t('board.masters.add')"
        @click="openCreateForm"
      >
        + {{ t('board.masters.add') }}
      </el-button>
    </div>

    <!-- 검색 결과 스크린 리더 알림 — KWCAG aria-live -->
    <div aria-live="polite" aria-atomic="true" class="sr-only">
      {{ liveAnnouncement }}
    </div>

    <!-- 게시판 마스터 테이블 -->
    <el-table
      v-loading="loading"
      :data="masters"
      stripe
      :empty-text="t('board.masters.empty')"
      :aria-label="t('board.masters.title')"
      class="w-full"
      @row-click="goPostList"
    >
      <caption class="sr-only">{{ t('board.masters.title') }}</caption>

      <el-table-column
        prop="code"
        :label="t('board.masters.field.code')"
        min-width="120"
      />
      <el-table-column
        prop="name"
        :label="t('board.masters.field.name')"
        min-width="150"
      />
      <el-table-column
        prop="type"
        :label="t('board.masters.field.type')"
        width="110"
      >
        <template #default="{ row }">
          <!-- KWCAG: 색상 + 텍스트 동시 제공 -->
          <el-tag :type="typeTagType(row.type)" size="small">
            {{ t(`board.masters.type.${row.type}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="useComment"
        :label="t('board.masters.field.useComment')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <span :aria-label="row.useComment ? t('common.yes') : t('common.no')">
            {{ row.useComment ? '✓' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        prop="useAttachment"
        :label="t('board.masters.field.useAttachment')"
        width="110"
        align="center"
      >
        <template #default="{ row }">
          <span :aria-label="row.useAttachment ? t('common.yes') : t('common.no')">
            {{ row.useAttachment ? '✓' : '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column
        prop="status"
        :label="t('board.masters.field.status')"
        width="90"
      >
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdAt"
        :label="t('board.masters.field.createdAt')"
        min-width="140"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>

      <!-- 액션 컬럼 — SUPER_ADMIN만 노출 -->
      <el-table-column
        v-if="isSuperAdmin"
        :label="t('common.actions')"
        width="160"
        fixed="right"
      >
        <template #default="{ row }">
          <div class="flex gap-1">
            <el-button
              size="small"
              type="primary"
              plain
              :aria-label="`${t('board.masters.edit')} ${row.name}`"
              @click.stop="openEditForm(row)"
            >
              {{ t('board.masters.edit') }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              :aria-label="`${t('board.masters.delete')} ${row.name}`"
              @click.stop="handleDelete(row)"
            >
              {{ t('board.masters.delete') }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 빈 상태 -->
    <el-empty
      v-if="!loading && masters.length === 0"
      :description="t('board.masters.empty')"
      :image-size="120"
      class="mt-8"
    />

    <!-- 게시판 생성/수정 모달 -->
    <BoardFormView
      v-if="showForm"
      :mode="formMode"
      :master="selectedMaster"
      @close="showForm = false"
      @saved="onMasterSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { boardApi } from '@/api/board'
import BoardFormView from './BoardFormView.vue'
import type { BbsMasterSummary, BbsType } from '@iroum/shared/types/api'

const { t } = useI18n()
const router = useRouter()
const auth = useAuthStore()

const masters = ref<BbsMasterSummary[]>([])
const loading = ref(false)
const liveAnnouncement = ref('')
const showForm = ref(false)
const formMode = ref<'create' | 'edit'>('create')
const selectedMaster = ref<BbsMasterSummary | null>(null)

const isSuperAdmin = computed(() =>
  auth.user?.roleCodes?.includes('SUPER_ADMIN') ?? false,
)

// @MX:ANCHOR: [AUTO] loadMasters — onMounted, onMasterSaved에서 호출
// @MX:REASON: fan_in >= 3: 마운트, 저장 후 갱신, 삭제 후 갱신에서 호출
async function loadMasters(): Promise<void> {
  loading.value = true
  try {
    const res = await boardApi.listMasters()
    masters.value = res.data
    liveAnnouncement.value = t('board.masters.loaded', { count: res.data.length })
  } catch {
    ElMessage.error(t('board.masters.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function goPostList(row: BbsMasterSummary): void {
  router.push({ name: 'board-posts', params: { bbsId: row.id } })
}

function openCreateForm(): void {
  formMode.value = 'create'
  selectedMaster.value = null
  showForm.value = true
}

function openEditForm(master: BbsMasterSummary): void {
  formMode.value = 'edit'
  selectedMaster.value = master
  showForm.value = true
}

function onMasterSaved(): void {
  showForm.value = false
  loadMasters()
}

async function handleDelete(master: BbsMasterSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('board.masters.confirm.delete', { name: master.name }),
      t('board.masters.delete'),
      { type: 'warning', confirmButtonText: t('board.masters.delete'), cancelButtonText: t('common.cancel') },
    )
    await boardApi.deleteMaster(master.id)
    ElMessage.success(t('board.masters.success.deleted'))
    loadMasters()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(t('board.masters.error.deleteFailed'))
  }
}

function typeTagType(type: BbsType): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<BbsType, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    NORMAL: '',
    NOTICE: 'warning',
    QNA: 'info',
    FAQ: 'success',
    GALLERY: '',
    PUBLICATION: 'info',
    SURVEY: 'warning',
  }
  return map[type] ?? ''
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  })
}

onMounted(() => {
  loadMasters()
})
</script>
