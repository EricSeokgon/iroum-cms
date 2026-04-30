<template>
  <!-- 시스템 설정 — SPEC-CMS-005 Bundle D REQ-SYS-004-D -->
  <div>
    <h2 class="mb-4 text-xl font-semibold text-gray-800">{{ t('system.setting.title') }}</h2>

    <!-- 카테고리 탭 -->
    <el-tabs v-model="activeCategory" class="mb-4" @tab-click="onTabChange">
      <el-tab-pane
        v-for="cat in categories"
        :key="cat"
        :label="cat"
        :name="cat"
      />
    </el-tabs>

    <!-- 설정 목록 -->
    <el-card shadow="never" v-loading="loading">
      <el-table :data="filteredSettings" stripe size="small">
        <el-table-column prop="key" :label="t('system.setting.col.key')" width="220" />
        <el-table-column :label="t('system.setting.col.value')" min-width="200">
          <template #default="{ row }">
            <!-- STRING -->
            <el-input
              v-if="row.value_type === 'STRING'"
              v-model="row._editValue"
              size="small"
              @blur="updateSetting(row)"
            />
            <!-- INT -->
            <el-input-number
              v-else-if="row.value_type === 'INT'"
              v-model="row._editValue"
              :controls="false"
              size="small"
              @blur="updateSetting(row)"
            />
            <!-- BOOL -->
            <el-switch
              v-else-if="row.value_type === 'BOOL'"
              :model-value="row._editValue === 'true'"
              @change="(v: boolean) => { row._editValue = v ? 'true' : 'false'; updateSetting(row) }"
            />
            <!-- JSON -->
            <div v-else-if="row.value_type === 'JSON'">
              <el-button size="small" @click="openJsonEdit(row)">{{ t('system.setting.editJson') }}</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="value_type" :label="t('system.setting.col.type')" width="80" />
        <el-table-column prop="description" :label="t('system.setting.col.description')" min-width="160" show-overflow-tooltip />
        <el-table-column prop="updated_at" :label="t('system.setting.col.updatedAt')" width="160">
          <template #default="{ row }">{{ row.updated_at ? formatDate(row.updated_at) : '-' }}</template>
        </el-table-column>
        <el-table-column prop="updated_by" :label="t('system.setting.col.updatedBy')" width="120" />
      </el-table>
    </el-card>

    <!-- JSON 편집 다이얼로그 -->
    <el-dialog
      v-model="jsonDialogVisible"
      :title="t('system.setting.editJson')"
      width="560px"
    >
      <JsonValueEditor v-model="jsonEditValue" @valid="onJsonValid" />
      <template #footer>
        <el-button @click="jsonDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!!jsonError" @click="saveJson">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { settings } from '@/api/system'
import type { SystemSettingResponse } from '@/api/system'
import JsonValueEditor from '@/components/system/JsonValueEditor.vue'

const { t } = useI18n()

type SettingRow = SystemSettingResponse & { _editValue: string }

const allSettings = ref<SettingRow[]>([])
const loading = ref(false)
const activeCategory = ref('')

const categories = computed(() => {
  const cats = [...new Set(allSettings.value.map(s => s.category))]
  return cats.sort()
})

const filteredSettings = computed(() =>
  activeCategory.value
    ? allSettings.value.filter(s => s.category === activeCategory.value)
    : allSettings.value
)

const jsonDialogVisible = ref(false)
const jsonEditValue = ref('')
const jsonError = ref(false)
const editingJsonRow = ref<SettingRow | null>(null)

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR')
}

async function loadSettings(): Promise<void> {
  loading.value = true
  try {
    const res = await settings.list()
    allSettings.value = res.data.map(s => ({ ...s, _editValue: s.value }))
    if (categories.value.length > 0 && !activeCategory.value) {
      activeCategory.value = categories.value[0]
    }
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    loading.value = false
  }
}

function onTabChange(): void {
  // 탭 전환 시 별도 처리 없음 (filteredSettings computed 자동 반영)
}

async function updateSetting(row: SettingRow): Promise<void> {
  try {
    await settings.update(row.key, String(row._editValue), row.value_type)
    ElMessage.success(t('system.setting.saveSuccess'))
  } catch {
    ElMessage.error(t('common.saveError'))
    // 실패 시 원래 값 복원
    row._editValue = row.value
  }
}

function openJsonEdit(row: SettingRow): void {
  editingJsonRow.value = row
  jsonEditValue.value = row._editValue
  jsonError.value = false
  jsonDialogVisible.value = true
}

function onJsonValid(_parsed: unknown): void {
  jsonError.value = false
}

async function saveJson(): Promise<void> {
  if (!editingJsonRow.value) return
  try {
    JSON.parse(jsonEditValue.value)
  } catch {
    ElMessage.error(t('system.setting.jsonInvalid'))
    jsonError.value = true
    return
  }
  await updateSetting({ ...editingJsonRow.value, _editValue: jsonEditValue.value })
  editingJsonRow.value._editValue = jsonEditValue.value
  jsonDialogVisible.value = false
}

onMounted(() => loadSettings())
</script>
