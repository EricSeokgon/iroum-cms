<template>
  <!-- 공유 콘텐츠 블록 관리 — SPEC-CMS-CONTENT-BLOCK-001 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">콘텐츠 블록 관리</h2>
      <el-button type="primary" @click="openCreate">+ 블록 추가</el-button>
    </div>

    <!-- 필터 -->
    <div class="mb-4 flex gap-3">
      <el-select
        v-model="filterStatus"
        placeholder="상태 전체"
        clearable
        style="width: 160px"
        @change="load"
        @clear="load"
      >
        <el-option label="ACTIVE" value="ACTIVE" />
        <el-option label="INACTIVE" value="INACTIVE" />
      </el-select>
      <el-select
        v-model="filterType"
        placeholder="타입 전체"
        clearable
        style="width: 180px"
        @change="load"
        @clear="load"
      >
        <el-option v-for="t in BLOCK_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
      <el-button @click="load">검색</el-button>
    </div>

    <el-table
      v-loading="loading"
      :data="blockList"
      stripe
      empty-text="등록된 블록이 없습니다"
      aria-label="콘텐츠 블록 목록"
    >
      <el-table-column prop="name" label="이름" min-width="160" />
      <el-table-column prop="slug" label="슬러그" min-width="160">
        <template #default="{ row }">
          <span class="font-mono text-xs">{{ row.slug }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="blockType" label="타입" width="120" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ row.blockType }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="상태" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="생성일" width="180">
        <template #default="{ row }">
          <span class="text-xs text-gray-500">{{ formatDate(row.createdAt) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="작업" width="260" align="center">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">수정</el-button>
          <el-button size="small" @click="toggleStatus(row)">
            {{ row.status === 'ACTIVE' ? '비활성화' : '활성화' }}
          </el-button>
          <el-button size="small" type="danger" @click="remove(row)">삭제</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 생성/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogOpen"
      :title="form.id ? '블록 수정' : '블록 생성'"
      width="640px"
    >
      <el-form label-width="100px">
        <el-form-item label="이름" required>
          <el-input v-model="form.name" maxlength="200" />
        </el-form-item>
        <el-form-item label="슬러그" required>
          <el-input v-model="form.slug" placeholder="lower-case-with-hyphens" maxlength="100" />
        </el-form-item>
        <el-form-item label="타입" required>
          <el-select v-model="form.blockType" style="width: 100%">
            <el-option v-for="t in BLOCK_TYPES" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="form.blockType === 'RICH_TEXT' || form.blockType === 'HTML'"
          label="HTML 내용"
        >
          <el-input v-model="form.contentHtml" type="textarea" :rows="6" />
        </el-form-item>
        <el-form-item
          v-if="form.blockType === 'MARKDOWN' || form.blockType === 'EMBED'"
          :label="form.blockType === 'EMBED' ? 'EMBED URL' : '원본 내용'"
        >
          <el-input v-model="form.contentRaw" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="설명">
          <el-input v-model="form.description" maxlength="500" />
        </el-form-item>
        <el-form-item v-if="form.id" label="상태">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="INACTIVE" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogOpen = false">취소</el-button>
        <el-button type="primary" :loading="saving" @click="save">저장</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  contentBlockApi,
  type SharedContentBlockRequest,
  type SharedContentBlockResponse,
  type SharedBlockType,
} from '@/api/content'

const BLOCK_TYPES: SharedBlockType[] = ['RICH_TEXT', 'HTML', 'MARKDOWN', 'EMBED']

const loading = ref(false)
const saving = ref(false)
const dialogOpen = ref(false)
const blockList = ref<SharedContentBlockResponse[]>([])
const filterStatus = ref<string>('')
const filterType = ref<string>('')

interface BlockForm extends SharedContentBlockRequest {
  id?: number
}

const emptyForm = (): BlockForm => ({
  name: '',
  slug: '',
  blockType: 'RICH_TEXT',
  contentHtml: '',
  contentRaw: '',
  description: '',
  status: 'ACTIVE',
})

const form = ref<BlockForm>(emptyForm())

function formatDate(iso: string): string {
  return iso ? new Date(iso).toLocaleString('ko-KR') : ''
}

async function load() {
  loading.value = true
  try {
    const res = await contentBlockApi.list({
      status: filterStatus.value || undefined,
      type: filterType.value || undefined,
    })
    blockList.value = res.data
  } catch {
    ElMessage.error('블록 목록을 불러오지 못했습니다')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = emptyForm()
  dialogOpen.value = true
}

function openEdit(row: SharedContentBlockResponse) {
  form.value = {
    id: row.id,
    name: row.name,
    slug: row.slug,
    blockType: row.blockType as SharedBlockType,
    contentHtml: row.contentHtml ?? '',
    contentRaw: row.contentRaw ?? '',
    description: row.description ?? '',
    status: row.status,
  }
  dialogOpen.value = true
}

async function save() {
  saving.value = true
  try {
    const payload: SharedContentBlockRequest = {
      name: form.value.name,
      slug: form.value.slug,
      blockType: form.value.blockType,
      contentHtml: form.value.contentHtml || undefined,
      contentRaw: form.value.contentRaw || undefined,
      description: form.value.description || undefined,
      status: form.value.status,
    }
    if (form.value.id) {
      await contentBlockApi.update(form.value.id, payload)
    } else {
      await contentBlockApi.create(payload)
    }
    ElMessage.success('저장되었습니다')
    dialogOpen.value = false
    await load()
  } catch {
    ElMessage.error('저장에 실패했습니다')
  } finally {
    saving.value = false
  }
}

async function toggleStatus(row: SharedContentBlockResponse) {
  const next = row.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'
  try {
    await contentBlockApi.updateStatus(row.id, next)
    ElMessage.success('상태가 변경되었습니다')
    await load()
  } catch {
    ElMessage.error('상태 변경에 실패했습니다')
  }
}

async function remove(row: SharedContentBlockResponse) {
  try {
    await ElMessageBox.confirm(`'${row.name}' 블록을 삭제하시겠습니까?`, '삭제 확인', {
      type: 'warning',
      confirmButtonText: '삭제',
      cancelButtonText: '취소',
    })
  } catch {
    return
  }
  try {
    await contentBlockApi.delete(row.id)
    ElMessage.success('삭제되었습니다')
    await load()
  } catch {
    ElMessage.error('삭제에 실패했습니다')
  }
}

onMounted(load)
</script>
