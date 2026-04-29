<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('media.collections.title') }}</h2>
      <el-button type="primary" @click="showCreate = true">
        + {{ t('media.collections.create') }}
      </el-button>
    </div>

    <div v-loading="loading" class="min-h-[200px]">
      <el-table
        :data="collections"
        stripe
        :empty-text="t('media.collections.empty')"
        :aria-label="t('media.collections.title')"
        class="w-full"
      >
        <el-table-column prop="name" :label="t('media.collections.field.name')" min-width="180" />
        <el-table-column prop="description" :label="t('media.collections.field.description')" min-width="260">
          <template #default="{ row }">
            {{ row.description ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="itemCount" :label="t('media.collections.field.itemCount')" width="100" />
        <el-table-column prop="createdAt" :label="t('media.collections.field.createdAt')" width="160">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 컬렉션 생성 다이얼로그 -->
    <!-- @MX:TODO: [AUTO] 컬렉션 상세 편집 (아이템 추가/제거) — 2차 단계 구현 예정 -->
    <el-dialog
      v-model="showCreate"
      :title="t('media.collections.create')"
      width="420px"
      :aria-label="t('media.collections.create')"
    >
      <el-form :model="createForm" label-width="100px">
        <el-form-item :label="t('media.collections.field.name')" required>
          <el-input v-model="createForm.name" :aria-label="t('media.collections.field.name')" aria-required="true" />
        </el-form-item>
        <el-form-item :label="t('media.collections.field.description')">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="2"
            :aria-label="t('media.collections.field.description')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { mediaApi } from '@/api/media'
import type { MediaCollectionSummary } from '@iroum/shared/types/api'

const { t } = useI18n()

const collections = ref<MediaCollectionSummary[]>([])
const loading = ref(false)
const showCreate = ref(false)
const creating = ref(false)

const createForm = ref({ name: '', description: '' })

async function loadCollections(): Promise<void> {
  loading.value = true
  try {
    const res = await mediaApi.listCollections()
    collections.value = res.data
  } catch {
    ElMessage.error(t('media.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

async function handleCreate(): Promise<void> {
  if (!createForm.value.name.trim()) return
  creating.value = true
  try {
    await mediaApi.createCollection(createForm.value.name, createForm.value.description || undefined)
    ElMessage.success(t('media.collections.created'))
    showCreate.value = false
    createForm.value = { name: '', description: '' }
    await loadCollections()
  } catch {
    ElMessage.error(t('media.error.loadFailed'))
  } finally {
    creating.value = false
  }
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  })
}

onMounted(loadCollections)
</script>
