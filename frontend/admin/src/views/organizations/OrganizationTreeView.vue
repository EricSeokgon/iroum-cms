<template>
  <div>
    <div class="mb-6 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">{{ t('organizations.title') }}</h2>
      <el-button type="primary" @click="openCreateRoot" :aria-label="t('organizations.action.addRoot')">
        + {{ t('organizations.action.addRoot') }}
      </el-button>
    </div>

    <div class="flex gap-4" style="min-height: 500px;">
      <!-- 좌측: 조직 트리 (60%) -->
      <div class="flex-[6] rounded-md border border-gray-200 bg-white p-4">
        <div v-if="treeLoading" class="flex items-center justify-center py-16">
          <el-icon class="animate-spin text-2xl text-blue-500"><i-ep-loading /></el-icon>
        </div>

        <el-empty
          v-else-if="treeData.length === 0"
          :description="t('organizations.tree.empty')"
          :image-size="80"
        />

        <el-tree
          v-else
          ref="treeRef"
          :data="treeData"
          :props="treeProps"
          node-key="id"
          default-expand-all
          highlight-current
          role="tree"
          :aria-label="t('organizations.title')"
          :aria-multiselectable="false"
          class="org-tree"
          @node-click="onNodeClick"
        >
          <template #default="{ node, data }">
            <div
              class="flex w-full items-center justify-between pr-2"
              :class="{ 'opacity-50': data.status !== 'ACTIVE' }"
              :role="'treeitem'"
              :aria-level="data.depth + 1"
              :aria-expanded="!node.isLeaf ? node.expanded : undefined"
            >
              <!-- 노드 레이블 -->
              <span class="flex items-center gap-2 truncate">
                <span
                  class="font-mono text-xs text-gray-400"
                  :class="{ 'line-through': data.status === 'DELETED' }"
                >{{ data.code }}</span>
                <span
                  class="font-medium"
                  :class="{
                    'text-gray-800': data.status === 'ACTIVE',
                    'text-gray-400 line-through': data.status !== 'ACTIVE',
                  }"
                >{{ data.name }}</span>
                <el-badge
                  v-if="data.children?.length"
                  :value="data.children.length"
                  type="info"
                  class="ml-1"
                />
              </span>

              <!-- 노드 액션 버튼 (hover 시 표시) -->
              <span class="node-actions flex items-center gap-1">
                <el-button
                  size="small"
                  type="primary"
                  text
                  :aria-label="`${t('organizations.action.addChild')} (${data.name})`"
                  @click.stop="openCreateChild(data)"
                >
                  <el-icon><i-ep-plus /></el-icon>
                </el-button>
                <el-button
                  size="small"
                  type="warning"
                  text
                  :aria-label="`${t('organizations.action.edit')} (${data.name})`"
                  @click.stop="openEdit(data)"
                >
                  <el-icon><i-ep-edit /></el-icon>
                </el-button>
                <el-button
                  size="small"
                  type="info"
                  text
                  :aria-label="`${t('organizations.action.viewHistory')} (${data.name})`"
                  @click.stop="openHistory(data)"
                >
                  <el-icon><i-ep-clock /></el-icon>
                </el-button>
                <el-button
                  size="small"
                  type="danger"
                  text
                  :aria-label="`${t('organizations.action.delete')} (${data.name})`"
                  @click.stop="handleDelete(data)"
                >
                  <el-icon><i-ep-delete /></el-icon>
                </el-button>
              </span>
            </div>
          </template>
        </el-tree>
      </div>

      <!-- 우측: 선택된 노드 상세 (40%) -->
      <div class="flex-[4] rounded-md border border-gray-200 bg-white p-4">
        <div v-if="!selectedNode" class="flex h-full items-center justify-center text-gray-400">
          <div class="text-center">
            <el-icon class="mb-2 text-4xl"><i-ep-office-building /></el-icon>
            <p>{{ t('organizations.detail.selectHint') }}</p>
          </div>
        </div>

        <div v-else>
          <h3 class="mb-4 text-base font-semibold text-gray-800">
            {{ t('organizations.detail.title') }}
          </h3>

          <el-descriptions :column="1" border size="small">
            <el-descriptions-item :label="t('organizations.field.code')">
              <span class="font-mono">{{ selectedDetail?.code ?? selectedNode.code }}</span>
            </el-descriptions-item>
            <el-descriptions-item :label="t('organizations.field.name')">
              {{ selectedDetail?.name ?? selectedNode.name }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('organizations.field.status')">
              <el-tag :type="statusTagType(selectedNode.status)" size="small">
                {{ t(`organizations.status.${selectedNode.status}`) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="t('organizations.field.depth')">
              {{ selectedNode.depth }}
            </el-descriptions-item>
            <el-descriptions-item :label="t('organizations.field.sortOrder')">
              {{ selectedDetail?.sortOrder ?? selectedNode.sortOrder }}
            </el-descriptions-item>
            <el-descriptions-item v-if="selectedDetail?.path" :label="t('organizations.field.path')">
              <span class="text-xs text-gray-500">{{ selectedDetail.path }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="selectedDetail?.description" :label="t('organizations.field.description')">
              {{ selectedDetail.description }}
            </el-descriptions-item>
            <el-descriptions-item v-if="selectedDetail?.createdAt" :label="t('organizations.field.createdAt')">
              {{ formatDate(selectedDetail.createdAt) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="selectedDetail?.updatedAt" :label="t('organizations.field.updatedAt')">
              {{ formatDate(selectedDetail.updatedAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="mt-4 flex flex-wrap gap-2">
            <el-button size="small" type="primary" @click="openCreateChild(selectedNode)">
              {{ t('organizations.action.addChild') }}
            </el-button>
            <el-button size="small" type="warning" @click="openEdit(selectedNode)">
              {{ t('organizations.action.edit') }}
            </el-button>
            <el-button size="small" type="info" @click="openHistory(selectedNode)">
              {{ t('organizations.action.viewHistory') }}
            </el-button>
            <el-button size="small" type="danger" @click="handleDelete(selectedNode)">
              {{ t('organizations.action.delete') }}
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 조직 폼 모달 -->
    <OrganizationFormView
      v-if="showForm"
      :mode="formMode"
      :parent-node="formParentNode"
      :edit-id="formEditId"
      @close="showForm = false"
      @saved="onSaved"
    />

    <!-- 변경 이력 모달 -->
    <OrganizationHistoryDialog
      v-if="showHistory && historyOrgId !== null"
      :org-id="historyOrgId"
      :org-name="historyOrgName"
      @close="showHistory = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree } from 'element-plus'
import axios from 'axios'
import { organizationsApi } from '@/api/organizations'
import OrganizationFormView from './OrganizationFormView.vue'
import OrganizationHistoryDialog from './OrganizationHistoryDialog.vue'
import type { OrganizationTreeNode, OrganizationDetail } from '@iroum/shared/types/api'

const { t } = useI18n()

// ── 트리 상태 ──────────────────────────────────────────────────────────────────
const treeRef = ref<InstanceType<typeof ElTree>>()
const treeData = ref<OrganizationTreeNode[]>([])
const treeLoading = ref(false)
const treeProps = { children: 'children', label: 'name' }

// 선택된 노드
const selectedNode = ref<OrganizationTreeNode | null>(null)
const selectedDetail = ref<OrganizationDetail | null>(null)

// 폼 모달 상태
const showForm = ref(false)
const formMode = ref<'create-root' | 'create-child' | 'edit'>('create-root')
const formParentNode = ref<OrganizationTreeNode | null>(null)
const formEditId = ref<number | null>(null)

// 변경 이력 모달 상태
const showHistory = ref(false)
const historyOrgId = ref<number | null>(null)
const historyOrgName = ref('')

// @MX:ANCHOR: [AUTO] loadTree — onMounted, onSaved 후 트리 갱신 시 호출
// @MX:REASON: fan_in >= 3: 마운트, 생성/수정/삭제 저장 완료 후 3곳에서 호출
async function loadTree(): Promise<void> {
  treeLoading.value = true
  try {
    const res = await organizationsApi.tree()
    treeData.value = res.data
  } catch {
    ElMessage.error(t('organizations.error.loadFailed'))
  } finally {
    treeLoading.value = false
  }
}

async function onNodeClick(data: OrganizationTreeNode): Promise<void> {
  selectedNode.value = data
  selectedDetail.value = null
  try {
    const res = await organizationsApi.detail(data.id)
    selectedDetail.value = res.data
  } catch {
    // 상세 조회 실패해도 트리 노드 기본값으로 표시 유지
  }
}

// ── 폼 모달 ────────────────────────────────────────────────────────────────────
function openCreateRoot(): void {
  formMode.value = 'create-root'
  formParentNode.value = null
  formEditId.value = null
  showForm.value = true
}

function openCreateChild(node: OrganizationTreeNode): void {
  formMode.value = 'create-child'
  formParentNode.value = node
  formEditId.value = null
  showForm.value = true
}

function openEdit(node: OrganizationTreeNode): void {
  formMode.value = 'edit'
  formParentNode.value = null
  formEditId.value = node.id
  showForm.value = true
}

function openHistory(node: OrganizationTreeNode): void {
  historyOrgId.value = node.id
  historyOrgName.value = node.name
  showHistory.value = true
}

function onSaved(): void {
  showForm.value = false
  loadTree()
}

// ── 삭제 ────────────────────────────────────────────────────────────────────────

// @MX:WARN: [AUTO] handleDelete — 409 hasChildren/hasUsers 분기 처리, 미처리 시 백엔드 에러 노출
// @MX:REASON: DELETE 409 응답 코드별로 사용자 안내 메시지가 다르므로 분기 필수
async function handleDelete(node: OrganizationTreeNode): Promise<void> {
  try {
    await ElMessageBox.confirm(
      t('organizations.confirm.delete', { name: node.name }),
      t('organizations.action.delete'),
      {
        type: 'warning',
        confirmButtonText: t('organizations.action.delete'),
        cancelButtonText: t('common.cancel'),
      },
    )
    await organizationsApi.delete(node.id)
    ElMessage.success(t('organizations.success.deleted'))
    if (selectedNode.value?.id === node.id) {
      selectedNode.value = null
      selectedDetail.value = null
    }
    loadTree()
  } catch (e) {
    if (e === 'cancel') return
    if (axios.isAxiosError(e)) {
      const code = e.response?.data?.code ?? ''
      if (code === 'HAS_CHILDREN') {
        ElMessage.error(t('organizations.error.hasChildren'))
      } else if (code === 'HAS_USERS') {
        ElMessage.error(t('organizations.error.hasUsers'))
      } else {
        ElMessage.error(t('organizations.error.deleteFailed'))
      }
    } else {
      ElMessage.error(t('organizations.error.deleteFailed'))
    }
  }
}

// ── 유틸 ────────────────────────────────────────────────────────────────────────
function statusTagType(status: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    ACTIVE: 'success',
    INACTIVE: 'info',
    DELETED: 'danger',
  }
  return map[status] ?? ''
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('ko-KR', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  })
}

onMounted(loadTree)
</script>

<style scoped>
/* 노드 액션 버튼 — 호버 시에만 표시 */
.org-tree :deep(.el-tree-node__content) .node-actions {
  opacity: 0;
  transition: opacity 0.15s;
}

.org-tree :deep(.el-tree-node__content:hover) .node-actions,
.org-tree :deep(.el-tree-node__content:focus-within) .node-actions {
  opacity: 1;
}

/* 포커스 가시성 — KWCAG 2.4.7 */
.org-tree :deep(.el-tree-node:focus > .el-tree-node__content) {
  outline: 2px solid #3b82f6;
  outline-offset: -2px;
}
</style>
