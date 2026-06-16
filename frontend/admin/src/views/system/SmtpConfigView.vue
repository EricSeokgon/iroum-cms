<template>
  <div data-testid="smtp-config">
    <div class="mb-6">
      <h2 class="text-xl font-semibold text-gray-800">SMTP 설정</h2>
      <p class="mt-1 text-sm text-gray-500">
        이메일 발송에 사용할 SMTP 서버 정보를 설정합니다.
      </p>
    </div>

    <el-card v-loading="loading" style="max-width: 640px">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="140px"
        data-testid="smtp-config-form"
      >
        <el-form-item label="호스트" prop="host">
          <el-input v-model="form.host" placeholder="예: smtp.gmail.com" data-testid="smtp-host" />
        </el-form-item>
        <el-form-item label="포트" prop="port">
          <el-input-number
            v-model="form.port"
            :min="1"
            :max="65535"
            controls-position="right"
            data-testid="smtp-port"
          />
        </el-form-item>
        <el-form-item label="사용자명" prop="username">
          <el-input v-model="form.username" data-testid="smtp-username" />
        </el-form-item>
        <el-form-item label="비밀번호">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="변경 시에만 입력"
            data-testid="smtp-password"
          />
        </el-form-item>
        <el-form-item label="인증 사용">
          <el-checkbox v-model="form.auth" data-testid="smtp-auth">SMTP 인증 사용</el-checkbox>
        </el-form-item>
        <el-form-item label="STARTTLS">
          <el-checkbox v-model="form.starttls" data-testid="smtp-starttls">STARTTLS 사용</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="saving"
            data-testid="smtp-save-btn"
            @click="onSave"
          >
            저장
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
// SPEC-CMS-EMAIL-TEMPLATE-001 — SMTP 설정 화면
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { getSmtpConfig, updateSmtpConfig, type SmtpConfig } from '@/api/email-template'

const loading = ref(false)
const saving = ref(false)
const formRef = ref<FormInstance>()

const form = reactive<SmtpConfig>({
  host: '',
  port: 587,
  username: '',
  password: '',
  auth: true,
  starttls: true,
})

const rules: FormRules<SmtpConfig> = {
  host: [{ required: true, message: '호스트를 입력하세요.', trigger: 'blur' }],
  port: [{ required: true, message: '포트를 입력하세요.', trigger: 'blur' }],
  username: [{ required: true, message: '사용자명을 입력하세요.', trigger: 'blur' }],
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const { data } = await getSmtpConfig()
    form.host = data.host
    form.port = data.port
    form.username = data.username
    // GET 시 마스킹된 비밀번호는 폼에 반영하지 않음 (변경 시에만 입력)
    form.password = ''
    form.auth = data.auth
    form.starttls = data.starttls
  } catch {
    ElMessage.error('SMTP 설정을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

async function onSave(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    const payload: SmtpConfig = {
      host: form.host,
      port: form.port,
      username: form.username,
      auth: form.auth,
      starttls: form.starttls,
      // 비어 있으면 비밀번호 변경 없음 — 서버에서 기존 값 유지
      ...(form.password ? { password: form.password } : {}),
    }
    await updateSmtpConfig(payload)
    ElMessage.success('SMTP 설정이 저장되었습니다.')
    form.password = ''
  } catch {
    ElMessage.error('저장에 실패했습니다.')
  } finally {
    saving.value = false
  }
}
</script>
