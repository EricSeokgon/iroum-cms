<template>
  <!-- 다국어 리소스 편집기 — SPEC-CMS-004 REQ-CONTENT-008-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.i18n.title') }}</h2>
      <el-button type="primary" :loading="bulkSaving" @click="bulkSave">
        {{ t('content.i18n.bulkSave') }}
      </el-button>
    </div>

    <!-- 필터 -->
    <div class="mb-4 flex gap-3">
      <el-input
        v-model="searchNs"
        :placeholder="t('content.i18n.filterNamespace')"
        clearable
        style="width:200px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-input
        v-model="searchKey"
        :placeholder="t('content.i18n.filterKey')"
        clearable
        style="width:240px"
        @keyup.enter="load"
        @clear="load"
      />
      <el-button @click="load">{{ t('common.search') }}</el-button>
      <el-button @click="openAddField">+ {{ t('content.i18n.addField') }}</el-button>
    </div>

    <!-- 네임스페이스 × 리소스키 매트릭스 테이블 -->
    <div v-loading="loading" class="overflow-x-auto">
      <table class="min-w-full border-collapse text-sm" :aria-label="t('content.i18n.title')">
        <thead>
          <tr class="border-b border-gray-200 bg-gray-50">
            <th class="sticky left-0 bg-gray-50 px-3 py-2 text-left font-medium text-gray-600 min-w-48">
              {{ t('content.i18n.field.resourceId') }}
            </th>
            <th
              v-for="ns in namespaces"
              :key="ns"
              class="px-3 py-2 text-left font-medium text-gray-600 min-w-40"
            >
              {{ ns }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in matrix"
            :key="row.resourceId"
            class="border-b border-gray-100 hover:bg-gray-50"
          >
            <td class="sticky left-0 bg-white px-3 py-1.5 font-mono text-xs text-gray-700">
              {{ row.resourceId }}
            </td>
            <td
              v-for="ns in namespaces"
              :key="`${row.resourceId}-${ns}`"
              class="px-2 py-1"
            >
              <el-input
                v-model="edits[`${ns}::${row.resourceId}`]"
                size="small"
                :placeholder="t('content.i18n.emptyValue')"
                @change="markDirty(ns, row.resourceId)"
              />
            </td>
          </tr>
          <tr v-if="!loading && matrix.length === 0">
            <td :colspan="namespaces.length + 1" class="py-8 text-center text-gray-400">
              {{ t('content.i18n.empty') }}
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 페이지네이션 -->
    <div class="mt-4 flex justify-end">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="load"
        :aria-label="t('a11y.pagination')"
      />
    </div>

    <!-- 필드 추가 다이얼로그 -->
    <el-dialog
      v-model="addFieldOpen"
      :title="t('content.i18n.addFieldDialog.title')"
      width="480px"
    >
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="130px">
        <el-form-item :label="t('content.i18n.field.namespace')" prop="namespace">
          <el-input v-model="addForm.namespace" placeholder="common" />
        </el-form-item>
        <el-form-item :label="t('content.i18n.field.resourceId')" prop="resourceId">
          <el-input v-model="addForm.resourceId" placeholder="button.save" />
        </el-form-item>
        <el-form-item
          v-for="ns in namespaces"
          :key="ns"
          :label="ns"
        >
          <el-input v-model="addForm.values[ns]" :placeholder="t('content.i18n.emptyValue')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addFieldOpen = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="addingSaving" @click="submitAddField">
          {{ t('common.create') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { i18n as i18nApi } from '@/api/content'
import type { I18nResourceItem } from '@/api/content'

const { t } = useI18n()

// 지원 네임스페이스 (API에서 로드하거나 고정)
const namespaces = ref<string[]>(['ko', 'en'])

interface MatrixRow {
  resourceId: string
}

const matrix = ref<MatrixRow[]>([])
const edits = ref<Record<string, string>>({}) // `${ns}::${resourceId}` → value
const dirty = ref<Set<string>>(new Set()) // dirty key 추적
const loading = ref(false)
const bulkSaving = ref(false)
const searchNs = ref('')
const searchKey = ref('')
const currentPage = ref(1)
const pageSize = 30
const total = ref(0)

// 필드 추가
const addFieldOpen = ref(false)
const addingSaving = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = ref({ namespace: 'ko', resourceId: '', values: {} as Record<string, string> })

const addRules: FormRules = {
  namespace: [{ required: true, message: t('content.i18n.error.namespaceRequired'), trigger: 'blur' }],
  resourceId: [{ required: true, message: t('content.i18n.error.resourceIdRequired'), trigger: 'blur' }],
}

onMounted(load)

async function load(): Promise<void> {
  loading.value = true
  try {
    // 모든 네임스페이스 데이터 병렬 로드
    const results = await Promise.all(
      namespaces.value.map(ns =>
        i18nApi.list({
          namespace: searchNs.value || ns,
          resourceId: searchKey.value || undefined,
          page: currentPage.value - 1,
          size: pageSize,
        }).then(res => ({ ns, data: Array.isArray(res.data) ? res.data : (res.data as unknown as { content: I18nResourceItem[] }).content ?? [] }))
      )
    )

    // 리소스ID 수집
    const allIds = new Set<string>()
    results.forEach(({ data }: { ns: string; data: I18nResourceItem[] }) => {
      data.forEach((item: I18nResourceItem) => allIds.add(String(item.resourceId)))
    })

    // 매트릭스 구성
    matrix.value = [...allIds].map(id => ({ resourceId: id }))
    total.value = allIds.size

    // edits 맵 구성
    const newEdits: Record<string, string> = {}
    results.forEach(({ ns, data }: { ns: string; data: I18nResourceItem[] }) => {
      data.forEach((item: I18nResourceItem) => {
        newEdits[`${ns}::${String(item.resourceId)}`] = item.value ?? ''
      })
    })
    edits.value = newEdits
    dirty.value = new Set()
  } catch {
    ElMessage.error(t('content.i18n.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function markDirty(ns: string, resourceId: string): void {
  dirty.value.add(`${ns}::${resourceId}`)
}

async function bulkSave(): Promise<void> {
  if (dirty.value.size === 0) {
    ElMessage.info(t('content.i18n.noDirty'))
    return
  }
  bulkSaving.value = true
  try {
    // dirty 항목들만 upsert
    const items = [...dirty.value].map(key => {
      const [ns, resourceId] = key.split('::')
      return { namespace: ns, resourceId, value: edits.value[key] ?? '' }
    })
    await i18nApi.bulkUpsert(items)
    ElMessage.success(t('content.i18n.bulkSaved'))
    dirty.value = new Set()
  } catch {
    ElMessage.error(t('content.i18n.bulkSaveError'))
  } finally {
    bulkSaving.value = false
  }
}

function openAddField(): void {
  const initValues: Record<string, string> = {}
  namespaces.value.forEach(ns => { initValues[ns] = '' })
  addForm.value = { namespace: 'ko', resourceId: '', values: initValues }
  addFieldOpen.value = true
}

async function submitAddField(): Promise<void> {
  const valid = await addFormRef.value?.validate().catch(() => false)
  if (!valid) return
  addingSaving.value = true
  try {
    const items = namespaces.value
      .filter(ns => addForm.value.values[ns])
      .map(ns => ({
        namespace: ns,
        resourceId: addForm.value.resourceId,
        value: addForm.value.values[ns],
      }))
    if (items.length === 0) {
      ElMessage.warning(t('content.i18n.error.noValues'))
      return
    }
    await i18nApi.bulkUpsert(items)
    ElMessage.success(t('content.i18n.fieldAdded'))
    addFieldOpen.value = false
    await load()
  } catch {
    ElMessage.error(t('content.i18n.addError'))
  } finally {
    addingSaving.value = false
  }
}
</script>
