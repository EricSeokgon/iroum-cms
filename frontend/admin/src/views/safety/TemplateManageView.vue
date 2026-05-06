<template>
  <!-- 가이드라인 템플릿 관리 (Admin) — SPEC-CMS-006 -->
  <div>
    <div class="mb-4 flex items-center justify-between">
      <h2 class="text-xl font-semibold text-gray-800">가이드라인 템플릿 관리</h2>
      <el-button type="primary" :icon="Plus" @click="openCreate">신규 생성</el-button>
    </div>

    <el-card v-loading="store.templatesLoading" shadow="never">
      <el-table :data="store.templates" stripe empty-text="등록된 템플릿이 없습니다">
        <el-table-column prop="code" label="코드" width="160" />
        <el-table-column prop="name" label="이름" min-width="180" show-overflow-tooltip />
        <el-table-column label="대상 업종" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="ic in row.applicable_industry_codes"
              :key="ic"
              size="small"
              class="mr-1"
            >
              {{ ic }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="대상 등급" width="160">
          <template #default="{ row }">
            <el-tag
              v-for="g in row.applicable_grades"
              :key="g"
              size="small"
              type="warning"
              class="mr-1"
            >
              {{ g }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="버전" width="80" align="center" />
        <el-table-column prop="status" label="상태" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="작업" width="220" fixed="right">
          <template #default="{ row }">
            <div class="flex gap-1">
              <el-button size="small" type="primary" plain @click="openEdit(row)">수정</el-button>
              <el-button size="small" plain @click="handlePreview(row)">미리보기</el-button>
              <el-button size="small" type="danger" plain @click="handleDelete(row)">삭제</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 등록/수정 다이얼로그 -->
    <el-dialog
      v-model="dialogOpen"
      :title="dialogMode === 'create' ? '템플릿 등록' : '템플릿 수정'"
      width="800px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
        <el-form-item label="코드" prop="code">
          <el-input v-model="form.code" :disabled="dialogMode === 'edit'" />
        </el-form-item>
        <el-form-item label="이름" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="설명">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="대상 업종" prop="applicable_industry_codes">
          <el-select
            v-model="form.applicable_industry_codes"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="업종 코드 입력 후 Enter"
            style="width: 100%"
          >
            <el-option label="건설업" value="건설업" />
            <el-option label="제조업" value="제조업" />
            <el-option label="화학" value="화학" />
            <el-option label="전기" value="전기" />
            <el-option label="물류" value="물류" />
          </el-select>
        </el-form-item>
        <el-form-item label="대상 등급" prop="applicable_grades">
          <el-checkbox-group v-model="form.applicable_grades">
            <el-checkbox label="A" value="A">A</el-checkbox>
            <el-checkbox label="B" value="B">B</el-checkbox>
            <el-checkbox label="C" value="C">C</el-checkbox>
            <el-checkbox label="D" value="D">D</el-checkbox>
            <el-checkbox label="E" value="E">E</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="본문 템플릿" prop="body_template">
          <el-input
            v-model="form.body_template"
            type="textarea"
            :rows="8"
            placeholder="HTML 또는 Mustache/Handlebars 형식의 템플릿"
          />
        </el-form-item>
        <el-form-item label="상태">
          <el-select v-model="form.status" style="width: 200px">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DRAFT" value="DRAFT" />
            <el-option label="INACTIVE" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 체크리스트 항목 (수정 모드 전용) -->
      <div v-if="dialogMode === 'edit' && currentTemplateId" class="mt-4">
        <el-divider>체크리스트 항목</el-divider>
        <el-table :data="checklistItems" size="small">
          <el-table-column prop="code" label="코드" width="140" />
          <el-table-column prop="title" label="제목" min-width="200" />
          <el-table-column prop="sort_order" label="순서" width="80" align="center" />
          <el-table-column prop="required" label="필수" width="70" align="center">
            <template #default="{ row }">
              <span>{{ row.required ? '✓' : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="작업" width="80">
            <template #default="{ row }">
              <el-button size="small" type="danger" link @click="removeChecklistItem(row)">삭제</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="mt-3 flex flex-wrap gap-2 items-end">
          <el-input v-model="newItem.code" placeholder="코드" size="small" style="width: 140px" />
          <el-input v-model="newItem.title" placeholder="제목" size="small" style="width: 220px" />
          <el-input-number v-model="newItem.sort_order" :min="0" size="small" style="width: 90px" />
          <el-checkbox v-model="newItem.required">필수</el-checkbox>
          <el-button size="small" type="primary" @click="addChecklistItemAction">+ 항목 추가</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="dialogOpen = false">취소</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">저장</el-button>
      </template>
    </el-dialog>

    <!-- 미리보기 다이얼로그 -->
    <el-dialog v-model="previewOpen" title="템플릿 미리보기" width="900px">
      <div v-loading="previewing" class="report-body prose max-w-none" v-html="previewHtml" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useSafetyStore } from '@/stores/safetyStore'
import type { TemplateRequest, TemplateSummary, TemplateChecklistItem, RiskGrade } from '@/api/safety'

const store = useSafetyStore()

const dialogOpen = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const currentTemplateId = ref<number | null>(null)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<TemplateRequest>({
  code: '',
  name: '',
  description: '',
  applicable_industry_codes: [],
  applicable_grades: [] as RiskGrade[],
  body_template: '',
  status: 'DRAFT',
})

const rules: FormRules = {
  code: [{ required: true, message: '코드는 필수입니다', trigger: 'blur' }],
  name: [{ required: true, message: '이름은 필수입니다', trigger: 'blur' }],
  applicable_industry_codes: [
    { required: true, type: 'array', min: 1, message: '대상 업종을 1개 이상 선택하세요', trigger: 'change' },
  ],
  applicable_grades: [
    { required: true, type: 'array', min: 1, message: '대상 등급을 1개 이상 선택하세요', trigger: 'change' },
  ],
  body_template: [{ required: true, message: '본문 템플릿은 필수입니다', trigger: 'blur' }],
}

// 체크리스트 항목
const checklistItems = ref<TemplateChecklistItem[]>([])
const newItem = reactive<TemplateChecklistItem>({
  code: '',
  title: '',
  description: '',
  sort_order: 0,
  required: false,
})

// 미리보기
const previewOpen = ref(false)
const previewing = ref(false)
const previewHtml = ref('')

function resetForm(): void {
  form.code = ''
  form.name = ''
  form.description = ''
  form.applicable_industry_codes = []
  form.applicable_grades = []
  form.body_template = ''
  form.status = 'DRAFT'
  checklistItems.value = []
  currentTemplateId.value = null
}

function openCreate(): void {
  resetForm()
  dialogMode.value = 'create'
  dialogOpen.value = true
}

async function openEdit(row: TemplateSummary): Promise<void> {
  resetForm()
  dialogMode.value = 'edit'
  currentTemplateId.value = row.id
  await store.fetchTemplate(row.id)
  if (store.currentTemplate) {
    Object.assign(form, {
      code: store.currentTemplate.code,
      name: store.currentTemplate.name,
      description: store.currentTemplate.description ?? '',
      applicable_industry_codes: [...store.currentTemplate.applicable_industry_codes],
      applicable_grades: [...store.currentTemplate.applicable_grades],
      body_template: store.currentTemplate.body_template,
      status: store.currentTemplate.status,
    })
  }
  // 체크리스트 항목 별도 로드
  try {
    checklistItems.value = await store.fetchTemplateChecklist(row.id)
  } catch {
    checklistItems.value = []
  }
  dialogOpen.value = true
}

async function handleSave(): Promise<void> {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (dialogMode.value === 'create') {
        await store.createTemplate(form)
        ElMessage.success('템플릿이 생성되었습니다')
      } else if (currentTemplateId.value !== null) {
        await store.updateTemplate(currentTemplateId.value, form)
        ElMessage.success('템플릿이 수정되었습니다')
      }
      dialogOpen.value = false
      await store.fetchTemplates()
    } catch {
      ElMessage.error('저장 실패')
    } finally {
      saving.value = false
    }
  })
}

async function handleDelete(row: TemplateSummary): Promise<void> {
  try {
    await ElMessageBox.confirm(`템플릿 "${row.name}"을(를) 삭제하시겠습니까?`, '삭제 확인', {
      type: 'warning',
      confirmButtonText: '삭제',
      cancelButtonText: '취소',
    })
    await store.deleteTemplate(row.id)
    ElMessage.success('삭제되었습니다')
    await store.fetchTemplates()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('삭제 실패')
  }
}

async function handlePreview(row: TemplateSummary): Promise<void> {
  previewing.value = true
  previewOpen.value = true
  try {
    previewHtml.value = await store.previewTemplate(row.id)
  } catch {
    ElMessage.error('미리보기 생성 실패')
    previewHtml.value = '<p>미리보기를 불러오지 못했습니다.</p>'
  } finally {
    previewing.value = false
  }
}

async function addChecklistItemAction(): Promise<void> {
  if (!currentTemplateId.value) return
  if (!newItem.code || !newItem.title) {
    ElMessage.warning('코드와 제목은 필수입니다')
    return
  }
  try {
    const created = await store.addChecklistItem(currentTemplateId.value, { ...newItem })
    checklistItems.value.push(created)
    newItem.code = ''
    newItem.title = ''
    newItem.description = ''
    newItem.sort_order = 0
    newItem.required = false
  } catch {
    ElMessage.error('체크리스트 항목 추가 실패')
  }
}

async function removeChecklistItem(item: TemplateChecklistItem): Promise<void> {
  if (!currentTemplateId.value || !item.id) return
  try {
    await store.deleteTemplateChecklistItem(currentTemplateId.value, item.id)
    checklistItems.value = checklistItems.value.filter(i => i.id !== item.id)
  } catch {
    ElMessage.error('항목 삭제 실패')
  }
}

function statusType(s: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (s === 'ACTIVE') return 'success'
  if (s === 'DRAFT') return 'warning'
  return 'info'
}

onMounted(() => {
  store.fetchTemplates()
})
</script>
