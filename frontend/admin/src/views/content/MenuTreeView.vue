<template>
  <!-- 메뉴 트리 관리 — SPEC-CMS-004 REQ-CONTENT-001-D, 002-D -->
  <div class="flex gap-4">
    <!-- 좌측: 트리 -->
    <div class="w-72 rounded border border-gray-200 bg-white p-3">
      <div class="mb-3 flex items-center justify-between">
        <h3 class="font-medium text-gray-700">{{ t('content.menu.tree.title') }}</h3>
        <el-button size="small" type="primary" @click="openCreate(null)">
          + {{ t('content.menu.add') }}
        </el-button>
      </div>

      <div v-loading="menuStore.loading" class="min-h-40">
        <el-tree
          :data="menuStore.tree"
          :props="treeProps"
          node-key="id"
          highlight-current
          draggable
          :allow-drop="allowDrop"
          @node-click="selectNode"
          @node-drop="onDrop"
          :aria-label="t('content.menu.tree.label')"
          role="tree"
          empty-text=""
        >
          <template #default="{ node, data }">
            <span class="flex items-center gap-1.5 text-sm" :class="!data.isVisible ? 'text-gray-400' : ''">
              <span>{{ data.name }}</span>
              <el-tag v-if="!data.isVisible" size="small" type="info">{{ t('content.menu.hidden') }}</el-tag>
            </span>
          </template>
        </el-tree>
        <div v-if="menuStore.tree.length === 0 && !menuStore.loading" class="py-6 text-center text-sm text-gray-400">
          {{ t('content.menu.tree.empty') }}
        </div>
      </div>
    </div>

    <!-- 우측: 선택된 메뉴 상세 -->
    <div class="flex-1 rounded border border-gray-200 bg-white p-4">
      <div v-if="!selected" class="flex h-full items-center justify-center text-gray-400">
        {{ t('content.menu.selectHint') }}
      </div>

      <template v-else>
        <div class="mb-4 flex items-center justify-between">
          <h3 class="font-medium text-gray-700">{{ selected.name }}</h3>
          <div class="flex gap-2">
            <el-button size="small" @click="openPermissions">
              {{ t('content.menu.permissions') }}
            </el-button>
            <el-button size="small" @click="openCreate(selected.id)">
              + {{ t('content.menu.addChild') }}
            </el-button>
            <el-popconfirm
              :title="t('content.menu.deleteConfirm')"
              @confirm="deleteMenu"
            >
              <template #reference>
                <el-button size="small" type="danger" plain>{{ t('common.delete') }}</el-button>
              </template>
            </el-popconfirm>
          </div>
        </div>

        <!-- 상세 폼 -->
        <el-form :model="editForm" label-width="110px" size="small" @submit.prevent="updateMenu">
          <el-form-item :label="t('content.menu.field.code')">
            <el-input v-model="editForm.code" disabled />
          </el-form-item>
          <el-form-item :label="t('content.menu.field.name')">
            <el-input v-model="editForm.name" />
          </el-form-item>
          <el-form-item :label="t('content.menu.field.url')">
            <el-input v-model="editForm.url" :placeholder="t('content.menu.field.urlPlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('content.menu.field.target')">
            <el-select v-model="editForm.target" class="w-full">
              <el-option label="_self" value="_self" />
              <el-option label="_blank" value="_blank" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('content.menu.field.visible')">
            <el-switch
              v-model="editForm.isVisible"
              @change="toggleVisibility"
              :active-text="t('common.yes')"
              :inactive-text="t('common.no')"
              :aria-label="t('content.menu.field.visible')"
            />
          </el-form-item>
          <el-form-item :label="t('content.menu.field.depth')">
            <span class="text-sm text-gray-600">{{ selected.depth }}</span>
          </el-form-item>
          <el-form-item :label="t('content.menu.field.path')">
            <span class="text-xs text-gray-500 font-mono">{{ selected.path }}</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="saving" native-type="submit">{{ t('common.save') }}</el-button>
          </el-form-item>
        </el-form>
      </template>
    </div>
  </div>

  <!-- 메뉴 생성 다이얼로그 -->
  <el-dialog
    v-model="createOpen"
    :title="t('content.menu.createDialog.title')"
    width="480px"
    :close-on-click-modal="false"
  >
    <el-form
      ref="createFormRef"
      :model="createForm"
      :rules="createRules"
      label-width="110px"
    >
      <el-form-item :label="t('content.menu.field.code')" prop="code">
        <el-input v-model="createForm.code" />
      </el-form-item>
      <el-form-item :label="t('content.menu.field.name')" prop="name">
        <el-input v-model="createForm.name" />
      </el-form-item>
      <el-form-item :label="t('content.menu.field.url')">
        <el-input v-model="createForm.url" :placeholder="t('content.menu.field.urlPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('content.menu.field.target')">
        <el-select v-model="createForm.target" class="w-full">
          <el-option label="_self" value="_self" />
          <el-option label="_blank" value="_blank" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="createParentId" :label="t('content.menu.field.parent')">
        <span class="text-sm text-gray-600">{{ parentName }}</span>
        <el-alert
          v-if="parentDepth >= 5"
          type="error"
          :title="t('content.menu.createDialog.depthError')"
          :closable="false"
          class="mt-1"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createOpen = false">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="creating" :disabled="parentDepth >= 5" @click="createMenu">
        {{ t('common.create') }}
      </el-button>
    </template>
  </el-dialog>

  <!-- 권한 매핑 다이얼로그 -->
  <PermissionMappingDialog
    v-model="permOpen"
    :menu-id="selected?.id ?? null"
    :current-codes="currentPermCodes"
    @saved="loadTree"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import type { AllowDropType } from 'element-plus/es/components/tree/src/tree.type'
import { useMenuTreeStore, useSiteStore } from '@/stores/content'
import { menus } from '@/api/content'
import type { MenuTreeNode } from '@/api/content'
import PermissionMappingDialog from '@/components/content/PermissionMappingDialog.vue'

const { t } = useI18n()
const menuStore = useMenuTreeStore()
const siteStore = useSiteStore()

const selected = ref<MenuTreeNode | null>(null)
const permOpen = ref(false)
const createOpen = ref(false)
const saving = ref(false)
const creating = ref(false)
const createParentId = ref<number | null>(null)
const createFormRef = ref<FormInstance>()
const currentPermCodes = ref<string[]>([])

const treeProps = { label: 'name', children: 'children' }

const editForm = ref({
  code: '',
  name: '',
  url: '' as string | null,
  target: '_self' as '_self' | '_blank',
  isVisible: true,
})

const createForm = ref({
  code: '',
  name: '',
  url: '',
  target: '_self' as '_self' | '_blank',
})

const createRules: FormRules = {
  code: [{ required: true, message: t('content.menu.error.codeRequired'), trigger: 'blur' }],
  name: [{ required: true, message: t('content.menu.error.nameRequired'), trigger: 'blur' }],
}

// 부모 메뉴 정보
const parentNode = computed(() => {
  if (!createParentId.value) return null
  return findNode(menuStore.tree, createParentId.value)
})
const parentName = computed(() => parentNode.value?.name ?? '')
const parentDepth = computed(() => parentNode.value?.depth ?? 0)

function findNode(nodes: MenuTreeNode[], id: number): MenuTreeNode | null {
  for (const n of nodes) {
    if (n.id === id) return n
    const found = findNode(n.children, id)
    if (found) return found
  }
  return null
}

onMounted(async () => {
  await siteStore.fetchCurrent()
  await loadTree()
})

async function loadTree(): Promise<void> {
  const siteId = siteStore.currentSite?.id
  await menuStore.fetchTree({ siteId, context: 'ADMIN' })
}

function selectNode(node: MenuTreeNode): void {
  selected.value = node
  editForm.value = {
    code: node.code,
    name: node.name,
    url: node.url,
    target: node.target,
    isVisible: node.isVisible,
  }
}

function openCreate(parentId: number | null): void {
  createParentId.value = parentId
  createForm.value = { code: '', name: '', url: '', target: '_self' }
  createOpen.value = true
}

function openPermissions(): void {
  currentPermCodes.value = [] // 실제로는 API에서 조회
  permOpen.value = true
}

async function createMenu(): Promise<void> {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  const siteId = siteStore.currentSite?.id
  if (!siteId) return
  creating.value = true
  try {
    await menus.create({
      siteId,
      parentId: createParentId.value,
      code: createForm.value.code,
      name: createForm.value.name,
      url: createForm.value.url || undefined,
      target: createForm.value.target,
    })
    ElMessage.success(t('content.menu.created'))
    createOpen.value = false
    await loadTree()
  } catch {
    ElMessage.error(t('content.menu.createError'))
  } finally {
    creating.value = false
  }
}

async function updateMenu(): Promise<void> {
  if (!selected.value) return
  saving.value = true
  try {
    await menus.update(selected.value.id, {
      name: editForm.value.name,
      url: editForm.value.url ?? undefined,
      target: editForm.value.target,
    })
    ElMessage.success(t('content.menu.saved'))
    await loadTree()
  } catch {
    ElMessage.error(t('content.menu.saveError'))
  } finally {
    saving.value = false
  }
}

async function toggleVisibility(): Promise<void> {
  if (!selected.value) return
  try {
    await menus.toggleVisibility(selected.value.id)
    menuStore.invalidate()
    await loadTree()
  } catch {
    ElMessage.error(t('content.menu.toggleError'))
  }
}

async function deleteMenu(): Promise<void> {
  if (!selected.value) return
  try {
    await menus.delete(selected.value.id)
    ElMessage.success(t('content.menu.deleted'))
    selected.value = null
    await loadTree()
  } catch {
    ElMessage.error(t('content.menu.deleteError'))
  }
}

// ElTree allowDrop — depth 5 제한
function allowDrop(_draggingNode: unknown, dropNode: { data: MenuTreeNode }, type: AllowDropType): boolean {
  if (type === 'inner') {
    return (dropNode.data.depth ?? 0) < 5
  }
  return true
}

// 드래그앤드롭 → move API 호출
async function onDrop(
  draggingNode: { data: MenuTreeNode },
  dropNode: { data: MenuTreeNode },
  type: AllowDropType,
): Promise<void> {
  const dragId = draggingNode.data.id
  if (type === 'inner') {
    // 자식으로 이동
    await menus.move(dragId, dropNode.data.id).catch(() => {
      ElMessage.error(t('content.menu.moveError'))
    })
  } else {
    // 형제 순서 변경 — 간단 처리: 드롭 노드의 sortOrder 근처 (prev=앞, next=뒤)
    const newOrder = type === 'prev' ? dropNode.data.sortOrder : dropNode.data.sortOrder + 1
    await menus.changeOrder(dragId, newOrder).catch(() => {
      ElMessage.error(t('content.menu.moveError'))
    })
  }
  await loadTree()
}

watch(() => menuStore.errors, (err) => {
  if (err) ElMessage.error(err)
})
</script>
