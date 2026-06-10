<template>
  <div class="publication-category-manager">
    <div class="page-header">
      <h1>{{ t('board.publicationCategories.title') }}</h1>
      <el-button type="primary" @click="openCreateDialog(null)">
        {{ t('board.publicationCategories.action.create') }}
      </el-button>
    </div>

    <!-- 카테고리 트리 테이블 -->
    <el-table
      v-loading="loading"
      :data="flatList"
      row-key="id"
      border
      style="width: 100%"
    >
      <el-table-column prop="name" :label="t('board.publicationCategories.table.name')" min-width="200">
        <template #default="{ row }">
          <span :style="{ paddingLeft: `${(row.depth - 1) * 20}px` }">
            {{ row.depth > 1 ? '└ ' : '' }}{{ row.name }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="code" :label="t('board.publicationCategories.table.code')" width="160" />
      <el-table-column prop="depth" :label="t('board.publicationCategories.table.depth')" width="80" align="center" />
      <el-table-column prop="sortOrder" :label="t('board.publicationCategories.table.sortOrder')" width="100" align="center" />
      <el-table-column prop="status" :label="t('board.publicationCategories.table.status')" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">
            {{ t(`board.publicationCategories.status.${row.status}`) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('board.publicationCategories.table.action')" width="200" align="center">
        <template #default="{ row }">
          <el-button size="small" @click="openCreateDialog(row.id)">
            {{ t('board.publicationCategories.action.addChild') }}
          </el-button>
          <el-button size="small" type="warning" @click="openEditDialog(row)">
            {{ t('board.publicationCategories.action.edit') }}
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">
            {{ t('board.publicationCategories.action.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 생성/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editTarget ? t('board.publicationCategories.dialog.editTitle') : t('board.publicationCategories.dialog.createTitle')"
      width="480px"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item v-if="!editTarget" :label="t('board.publicationCategories.form.code')" prop="code">
          <el-input v-model="form.code" placeholder="CODE_EXAMPLE" style="text-transform:uppercase" />
        </el-form-item>
        <el-form-item :label="t('board.publicationCategories.form.name')" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item :label="t('board.publicationCategories.form.sortOrder')" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item v-if="editTarget" :label="t('board.publicationCategories.form.status')" prop="status">
          <el-select v-model="form.status">
            <el-option value="ACTIVE" :label="t('board.publicationCategories.status.ACTIVE')" />
            <el-option value="INACTIVE" :label="t('board.publicationCategories.status.INACTIVE')" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  publicationCategoryAdminApi,
  type PublicationCategoryDto,
  type CategoryStatus,
} from '@/api/publicationCategories'

const { t } = useI18n()

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const categories = ref<PublicationCategoryDto[]>([])
const editTarget = ref<PublicationCategoryDto | null>(null)
const selectedParentId = ref<number | null>(null)

const formRef = ref<FormInstance>()
const form = reactive({
  code: '',
  name: '',
  sortOrder: 0,
  status: 'ACTIVE' as CategoryStatus,
})

const rules: FormRules = {
  code: [{ required: true, message: t('board.publicationCategories.validation.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('board.publicationCategories.validation.nameRequired'), trigger: 'blur' }],
  status: [{ required: true, trigger: 'change' }],
}

/** 트리를 depth-first 순서로 평탄화 */
const flatList = computed(() => {
  const result: PublicationCategoryDto[] = []
  const flatten = (nodes: PublicationCategoryDto[]) => {
    nodes.forEach(node => {
      result.push(node)
      if (node.children?.length) flatten(node.children)
    })
  }
  flatten(categories.value)
  return result
})

async function loadCategories() {
  loading.value = true
  try {
    categories.value = await publicationCategoryAdminApi.listAll()
  } catch {
    ElMessage.error(t('board.publicationCategories.error.loadFailed'))
  } finally {
    loading.value = false
  }
}

function openCreateDialog(parentId: number | null) {
  editTarget.value = null
  selectedParentId.value = parentId
  Object.assign(form, { code: '', name: '', sortOrder: 0, status: 'ACTIVE' })
  dialogVisible.value = true
}

function openEditDialog(row: PublicationCategoryDto) {
  editTarget.value = row
  Object.assign(form, {
    code: row.code,
    name: row.name,
    sortOrder: row.sortOrder,
    status: row.status,
  })
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (editTarget.value) {
      await publicationCategoryAdminApi.update(editTarget.value.id, {
        name: form.name,
        sortOrder: form.sortOrder,
        status: form.status,
      })
      ElMessage.success(t('board.publicationCategories.success.updated'))
    } else {
      await publicationCategoryAdminApi.create({
        code: form.code.toUpperCase(),
        name: form.name,
        parentId: selectedParentId.value,
        sortOrder: form.sortOrder,
      })
      ElMessage.success(t('board.publicationCategories.success.created'))
    }
    dialogVisible.value = false
    await loadCategories()
  } catch (err: any) {
    const msg = err?.response?.data?.detail || t('board.publicationCategories.error.saveFailed')
    ElMessage.error(msg)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: PublicationCategoryDto) {
  try {
    await ElMessageBox.confirm(
      t('board.publicationCategories.confirm.delete', { name: row.name }),
      t('board.publicationCategories.confirm.deleteTitle'),
      { type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await publicationCategoryAdminApi.remove(row.id)
    ElMessage.success(t('board.publicationCategories.success.deleted'))
    await loadCategories()
  } catch (err: any) {
    const msg = err?.response?.data?.detail || t('board.publicationCategories.error.deleteFailed')
    ElMessage.error(msg)
  }
}

onMounted(loadCategories)
</script>

<style scoped>
.publication-category-manager {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-header h1 {
  margin: 0;
  font-size: 1.5rem;
}
</style>
