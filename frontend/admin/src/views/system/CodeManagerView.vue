<template>
  <!-- 공통 코드 관리 — SPEC-CMS-005 Bundle D REQ-SYS-003-D -->
  <div class="flex gap-4">
    <!-- 좌측: 그룹 목록 -->
    <el-card class="w-[520px] shrink-0" shadow="never">
      <template #header>
        <div class="flex items-center justify-between">
          <span class="font-medium">{{ t('system.code.group.title') }}</span>
          <el-button size="small" type="primary" @click="openGroupCreate">
            + {{ t('system.code.group.add') }}
          </el-button>
        </div>
      </template>

      <el-table
        :data="groupList"
        size="small"
        stripe
        highlight-current-row
        v-loading="groupLoading"
        @current-change="onGroupSelect"
      >
        <el-table-column prop="code" :label="t('system.code.group.col.code')" width="110" />
        <el-table-column prop="name" :label="t('system.code.group.col.name')" min-width="150" />
        <el-table-column :label="t('system.code.group.col.status')" width="80" align="center">
          <template #default="scope">
            <el-tag v-if="scope?.row" :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ scope.row.status === 'ACTIVE' ? t('common.active') : t('common.inactive') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.actions')" width="90" align="center">
          <template #default="scope">
            <el-button v-if="scope?.row" size="small" plain @click.stop="openGroupEdit(scope.row)">{{ t('common.edit') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 우측: 코드 목록 -->
    <div class="flex-1">
      <el-card shadow="never">
        <template #header>
          <div class="flex items-center justify-between">
            <span class="font-medium">
              {{ selectedGroup ? `[${selectedGroup.code}] ${selectedGroup.name}` : t('system.code.col.selectGroup') }}
            </span>
            <div class="flex gap-2">
              <el-button
                size="small"
                plain
                @click="bulkTestVisible = true"
              >
                {{ t('system.code.bulkTest') }}
              </el-button>
              <el-button
                v-if="selectedGroup"
                size="small"
                type="primary"
                @click="openCodeCreate"
              >
                + {{ t('system.code.add') }}
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="codeList" size="small" stripe v-loading="codeLoading">
          <el-table-column prop="code" :label="t('system.code.col.code')" width="100" />
          <el-table-column prop="name" :label="t('system.code.col.name')" />
          <el-table-column prop="value" :label="t('system.code.col.value')" width="120" show-overflow-tooltip />
          <el-table-column prop="sort_order" :label="t('system.code.col.sortOrder')" width="80" align="center" />
          <el-table-column :label="t('system.code.col.status')" width="80" align="center">
            <template #default="scope">
              <el-tag v-if="scope?.row" :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
                {{ scope.row.status === 'ACTIVE' ? t('common.active') : t('common.inactive') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.actions')" width="130" align="center">
            <template #default="scope">
              <template v-if="scope?.row">
                <el-button size="small" plain @click="openCodeEdit(scope.row)">{{ t('common.edit') }}</el-button>
                <el-popconfirm :title="t('system.code.deleteConfirm')" @confirm="deleteCode(scope.row)">
                  <template #reference>
                    <el-button size="small" type="danger" plain>{{ t('common.delete') }}</el-button>
                  </template>
                </el-popconfirm>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>

    <!-- 그룹 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="groupDialogVisible"
      :title="editingGroup ? t('system.code.group.edit') : t('system.code.group.add')"
      width="480px"
    >
      <el-form :model="groupForm" :rules="groupRules" label-width="100px" ref="groupFormRef">
        <el-form-item :label="t('system.code.group.col.code')" prop="code">
          <el-input v-model="groupForm.code" :disabled="!!editingGroup" />
        </el-form-item>
        <el-form-item :label="t('system.code.group.col.name')" prop="name">
          <el-input v-model="groupForm.name" />
        </el-form-item>
        <el-form-item :label="t('system.code.group.col.sortOrder')">
          <el-input-number v-model="groupForm.sort_order" :min="0" />
        </el-form-item>
        <el-form-item :label="t('system.code.group.col.status')">
          <el-switch
            v-model="groupForm.statusBool"
            :active-text="t('common.active')"
            :inactive-text="t('common.inactive')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="groupDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          v-if="editingGroup"
          type="danger"
          plain
          @click="deleteGroup(editingGroup)"
        >
          {{ t('common.delete') }}
        </el-button>
        <el-button type="primary" :loading="saving" @click="saveGroup">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 코드 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="codeDialogVisible"
      :title="editingCode ? t('system.code.edit') : t('system.code.add')"
      width="480px"
    >
      <el-form :model="codeForm" :rules="codeRules" label-width="100px" ref="codeFormRef">
        <el-form-item :label="t('system.code.col.code')" prop="code">
          <el-input v-model="codeForm.code" />
        </el-form-item>
        <el-form-item :label="t('system.code.col.name')" prop="name">
          <el-input v-model="codeForm.name" />
        </el-form-item>
        <el-form-item :label="t('system.code.col.value')">
          <el-input v-model="codeForm.value" />
        </el-form-item>
        <el-form-item :label="t('system.code.col.sortOrder')">
          <el-input-number v-model="codeForm.sort_order" :min="0" />
        </el-form-item>
        <el-form-item :label="t('system.code.col.status')">
          <el-switch
            v-model="codeForm.statusBool"
            :active-text="t('common.active')"
            :inactive-text="t('common.inactive')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="codeDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveCode">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 벌크 조회 테스트 다이얼로그 -->
    <el-dialog v-model="bulkTestVisible" :title="t('system.code.bulkTest')" width="600px">
      <div class="mb-3 flex gap-2">
        <el-input
          v-model="bulkInput"
          :placeholder="t('system.code.bulkTestPlaceholder')"
          clearable
        />
        <el-button type="primary" @click="runBulkTest" :loading="bulkLoading">
          {{ t('common.search') }}
        </el-button>
      </div>
      <pre v-if="bulkResult" class="rounded bg-gray-100 p-3 text-xs overflow-auto max-h-64">{{ JSON.stringify(bulkResult, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { codeGroups, codes } from '@/api/system'
import type { CodeGroupResponse, CodeResponse } from '@/api/system'

const { t } = useI18n()

// ── 그룹 상태 ────────────────────────────────────────────────────────────────
const groupList = ref<CodeGroupResponse[]>([])
const groupLoading = ref(false)
const groupDialogVisible = ref(false)
const editingGroup = ref<CodeGroupResponse | null>(null)
const groupFormRef = ref<FormInstance>()
const groupForm = ref({ code: '', name: '', sort_order: 0, statusBool: true })
const selectedGroup = ref<CodeGroupResponse | null>(null)

const groupRules: FormRules = {
  code: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  name: [{ required: true, message: t('common.required'), trigger: 'blur' }],
}

// ── 코드 상태 ────────────────────────────────────────────────────────────────
const codeList = ref<CodeResponse[]>([])
const codeLoading = ref(false)
const codeDialogVisible = ref(false)
const editingCode = ref<CodeResponse | null>(null)
const codeFormRef = ref<FormInstance>()
const codeForm = ref({ code: '', name: '', value: '', sort_order: 0, statusBool: true })

const codeRules: FormRules = {
  code: [{ required: true, message: t('common.required'), trigger: 'blur' }],
  name: [{ required: true, message: t('common.required'), trigger: 'blur' }],
}

const saving = ref(false)

// ── 벌크 테스트 ────────────────────────────────────────────────────────────
const bulkTestVisible = ref(false)
const bulkInput = ref('')
const bulkLoading = ref(false)
const bulkResult = ref<Record<string, CodeResponse[]> | null>(null)

async function loadGroups(): Promise<void> {
  groupLoading.value = true
  try {
    const res = await codeGroups.list()
    groupList.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    groupLoading.value = false
  }
}

async function onGroupSelect(row: CodeGroupResponse | null): Promise<void> {
  selectedGroup.value = row
  if (!row) { codeList.value = []; return }
  codeLoading.value = true
  try {
    const res = await codes.list(row.code)
    codeList.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    codeLoading.value = false
  }
}

function openGroupCreate(): void {
  editingGroup.value = null
  groupForm.value = { code: '', name: '', sort_order: 0, statusBool: true }
  groupDialogVisible.value = true
}

function openGroupEdit(row: CodeGroupResponse): void {
  editingGroup.value = row
  groupForm.value = {
    code: row.code,
    name: row.name,
    sort_order: row.sort_order,
    statusBool: row.status === 'ACTIVE',
  }
  groupDialogVisible.value = true
}

async function saveGroup(): Promise<void> {
  await groupFormRef.value?.validate()
  saving.value = true
  try {
    const payload = {
      code: groupForm.value.code,
      name: groupForm.value.name,
      sort_order: groupForm.value.sort_order,
      status: groupForm.value.statusBool ? 'ACTIVE' as const : 'INACTIVE' as const,
    }
    if (editingGroup.value) {
      await codeGroups.update(editingGroup.value.code, payload)
    } else {
      await codeGroups.create(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    groupDialogVisible.value = false
    await loadGroups()
  } catch (e: unknown) {
    const msg = (e as { response?: { status?: number } })?.response?.status === 409
      ? t('system.code.group.restrictError')
      : t('common.saveError')
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

async function deleteGroup(row: CodeGroupResponse): Promise<void> {
  try {
    await codeGroups.delete(row.code)
    ElMessage.success(t('common.deleteSuccess'))
    groupDialogVisible.value = false
    if (selectedGroup.value?.code === row.code) {
      selectedGroup.value = null
      codeList.value = []
    }
    await loadGroups()
  } catch (e: unknown) {
    const msg = (e as { response?: { status?: number } })?.response?.status === 409
      ? t('system.code.group.restrictError')
      : t('common.deleteError')
    ElMessage.error(msg)
  }
}

function openCodeCreate(): void {
  editingCode.value = null
  codeForm.value = { code: '', name: '', value: '', sort_order: 0, statusBool: true }
  codeDialogVisible.value = true
}

function openCodeEdit(row: CodeResponse): void {
  editingCode.value = row
  codeForm.value = {
    code: row.code,
    name: row.name,
    value: row.value ?? '',
    sort_order: row.sort_order,
    statusBool: row.status === 'ACTIVE',
  }
  codeDialogVisible.value = true
}

async function saveCode(): Promise<void> {
  await codeFormRef.value?.validate()
  if (!selectedGroup.value) return
  saving.value = true
  try {
    const payload = {
      group_code: selectedGroup.value.code,
      code: codeForm.value.code,
      name: codeForm.value.name,
      value: codeForm.value.value || undefined,
      sort_order: codeForm.value.sort_order,
      status: codeForm.value.statusBool ? 'ACTIVE' as const : 'INACTIVE' as const,
    }
    if (editingCode.value) {
      await codes.update(editingCode.value.id, payload)
    } else {
      await codes.create(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    codeDialogVisible.value = false
    await onGroupSelect(selectedGroup.value)
  } catch (e: unknown) {
    const msg = (e as { response?: { status?: number } })?.response?.status === 409
      ? t('system.code.uniqueError')
      : t('common.saveError')
    ElMessage.error(msg)
  } finally {
    saving.value = false
  }
}

async function deleteCode(row: CodeResponse): Promise<void> {
  try {
    await codes.delete(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    if (selectedGroup.value) await onGroupSelect(selectedGroup.value)
  } catch {
    ElMessage.error(t('common.deleteError'))
  }
}

async function runBulkTest(): Promise<void> {
  const groupCodes = bulkInput.value.split(',').map(s => s.trim()).filter(Boolean)
  if (groupCodes.length === 0) return
  bulkLoading.value = true
  try {
    const res = await codes.bulk(groupCodes)
    bulkResult.value = res.data
  } catch {
    ElMessage.error(t('common.loadError'))
  } finally {
    bulkLoading.value = false
  }
}

onMounted(() => loadGroups())
</script>
