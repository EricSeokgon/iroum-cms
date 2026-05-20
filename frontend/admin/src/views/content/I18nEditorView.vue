<template>
  <!-- 다국어 리소스 편집기 — SPEC-CMS-004 REQ-CONTENT-010-D -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('content.i18n.title') }}</h2>
      <el-button type="primary" :loading="bulkSaving" @click="bulkSave">
        {{ t('content.i18n.bulkSave') }}
      </el-button>
    </div>

    <!-- 필터 -->
    <div class="mb-4 flex gap-3 flex-wrap">
      <div>
        <p class="mb-1 text-xs text-gray-500">{{ t('content.i18n.field.namespace') }}</p>
        <el-select v-model="selectedNamespace" size="small" style="width:160px" @change="onNamespaceChange">
          <el-option v-for="ns in NAMESPACES" :key="ns" :label="ns" :value="ns" />
        </el-select>
      </div>
      <div class="flex items-end">
        <el-button @click="load">{{ t('common.search') }}</el-button>
        <el-button class="ml-2" @click="openAddField">+ {{ t('content.i18n.addField') }}</el-button>
      </div>
    </div>

    <!-- 매트릭스 테이블: rows=(resourceId, fieldName), cols=languages -->
    <div v-loading="loading" class="overflow-x-auto">
      <table class="min-w-full border-collapse text-sm" :aria-label="t('content.i18n.title')">
        <thead>
          <tr class="border-b border-gray-200 bg-gray-50">
            <th class="sticky left-0 bg-gray-50 px-3 py-2 text-left font-medium text-gray-600 w-16">ID</th>
            <th class="px-3 py-2 text-left font-medium text-gray-600 min-w-36">{{ t('content.i18n.field.fieldName') }}</th>
            <th
              v-for="lang in LANGUAGES"
              :key="lang"
              class="px-3 py-2 text-left font-medium text-gray-600 min-w-48"
            >
              {{ lang }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in matrix"
            :key="`${row.resourceId}-${row.fieldName}`"
            class="border-b border-gray-100 hover:bg-gray-50"
          >
            <td class="sticky left-0 bg-white px-3 py-1.5 font-mono text-xs text-gray-500">
              {{ row.resourceId }}
            </td>
            <td class="px-3 py-1.5 font-mono text-xs text-gray-700">{{ row.fieldName }}</td>
            <td
              v-for="lang in LANGUAGES"
              :key="`${row.resourceId}-${row.fieldName}-${lang}`"
              class="px-2 py-1"
            >
              <el-input
                v-model="edits[editKey(row.resourceId, row.fieldName, lang)]"
                size="small"
                :placeholder="t('content.i18n.emptyValue')"
                @change="markDirty(row.resourceId, row.fieldName, lang)"
              />
            </td>
          </tr>
          <tr v-if="!loading && matrix.length === 0">
            <td :colspan="LANGUAGES.length + 2" class="py-8 text-center text-gray-400">
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
      width="520px"
    >
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="130px">
        <el-form-item :label="t('content.i18n.field.namespace')" prop="namespace">
          <el-select v-model="addForm.namespace" style="width:100%">
            <el-option v-for="ns in NAMESPACES" :key="ns" :label="ns" :value="ns" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('content.i18n.field.resourceId')" prop="resourceId">
          <el-input-number v-model="addForm.resourceId" :min="1" :controls="false" style="width:100%" />
        </el-form-item>
        <el-form-item :label="t('content.i18n.field.fieldName')" prop="fieldName">
          <el-input v-model="addForm.fieldName" placeholder="title" />
        </el-form-item>
        <el-form-item
          v-for="lang in LANGUAGES"
          :key="lang"
          :label="lang"
        >
          <el-input v-model="addForm.values[lang]" :placeholder="t('content.i18n.emptyValue')" />
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

// 백엔드 CHECK 제약과 동일 (chk_i18n_namespace)
const NAMESPACES = ['menu', 'page', 'popup', 'banner', 'content_block', 'system'] as const
// 백엔드 CHECK 제약과 동일 (chk_i18n_language)
const LANGUAGES = ['ko', 'en'] as const

interface MatrixRow {
  resourceId: number
  fieldName: string
}

const selectedNamespace = ref<string>('system')
const matrix = ref<MatrixRow[]>([])
const edits = ref<Record<string, string>>({}) // `${resourceId}::${fieldName}::${lang}` → value
const dirty = ref<Set<string>>(new Set())
const loading = ref(false)
const bulkSaving = ref(false)
const currentPage = ref(1)
const pageSize = 50
const total = ref(0)

// 필드 추가
const addFieldOpen = ref(false)
const addingSaving = ref(false)
const addFormRef = ref<FormInstance>()
const addForm = ref({
  namespace: 'system',
  resourceId: 1,
  fieldName: '',
  values: {} as Record<string, string>,
})

const addRules: FormRules = {
  namespace:  [{ required: true, trigger: 'blur' }],
  resourceId: [{ required: true, type: 'number', trigger: 'blur' }],
  fieldName:  [{ required: true, message: t('content.i18n.error.fieldNameRequired'), trigger: 'blur' }],
}

function editKey(resourceId: number, fieldName: string, lang: string): string {
  return `${resourceId}::${fieldName}::${lang}`
}

onMounted(load)

function onNamespaceChange(): void {
  currentPage.value = 1
  load()
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const res = await i18nApi.listByNamespace({
      namespace: selectedNamespace.value,
      page: currentPage.value - 1,
      size: pageSize,
    })

    const items: I18nResourceItem[] = res.data.items
    total.value = res.data.total

    // 유니크 (resourceId, fieldName) 조합으로 행 구성
    const rowMap = new Map<string, MatrixRow>()
    items.forEach(item => {
      const key = `${item.resourceId}::${item.fieldName}`
      if (!rowMap.has(key)) {
        rowMap.set(key, { resourceId: Number(item.resourceId), fieldName: item.fieldName ?? '' })
      }
    })
    matrix.value = [...rowMap.values()]

    // edits 맵 구성
    const newEdits: Record<string, string> = {}
    items.forEach(item => {
      if (item.language) {
        newEdits[editKey(Number(item.resourceId), item.fieldName ?? '', item.language)] = item.value ?? ''
      }
    })
    edits.value = newEdits
    dirty.value = new Set()
  } catch {
    ElMessage.error(t('content.i18n.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function markDirty(resourceId: number, fieldName: string, lang: string): void {
  dirty.value.add(editKey(resourceId, fieldName, lang))
}

async function bulkSave(): Promise<void> {
  if (dirty.value.size === 0) {
    ElMessage.info(t('content.i18n.noDirty'))
    return
  }
  bulkSaving.value = true
  try {
    const items = [...dirty.value].map(key => {
      const [resourceId, fieldName, language] = key.split('::')
      return {
        namespace: selectedNamespace.value,
        resourceId: Number(resourceId),
        language,
        fieldName,
        value: edits.value[key] ?? '',
      }
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
  LANGUAGES.forEach(lang => { initValues[lang] = '' })
  addForm.value = { namespace: selectedNamespace.value, resourceId: 1, fieldName: '', values: initValues }
  addFieldOpen.value = true
}

async function submitAddField(): Promise<void> {
  const valid = await addFormRef.value?.validate().catch(() => false)
  if (!valid) return
  addingSaving.value = true
  try {
    const items = LANGUAGES
      .filter(lang => addForm.value.values[lang])
      .map(lang => ({
        namespace: addForm.value.namespace,
        resourceId: addForm.value.resourceId,
        language: lang,
        fieldName: addForm.value.fieldName,
        value: addForm.value.values[lang],
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
