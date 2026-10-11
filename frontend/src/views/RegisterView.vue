<template>
  <div class="register-page">
    <el-card class="register-card">
      <h2 class="register-title">注册账号</h2>
      <p class="register-sub">注册即成为学生用户，社团负责人角色由社联指派</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="submit"
      >
        <el-form-item label="学号" prop="studentNo" required>
          <el-input
            v-model="form.studentNo"
            placeholder="8-12 位数字"
            maxlength="12"
            clearable
          />
        </el-form-item>

        <el-form-item label="姓名" prop="realName" required>
          <el-input
            v-model="form.realName"
            placeholder="真实姓名"
            maxlength="20"
            clearable
          />
        </el-form-item>

        <el-form-item label="密码" prop="password" required>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="不少于 8 位，须包含字母和数字"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword" required>
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="手机号" prop="mobile" required>
          <el-input
            v-model="form.mobile"
            placeholder="11 位手机号"
            maxlength="11"
            clearable
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            class="register-btn"
            :loading="submitting"
            @click="submit"
          >
            注 册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="register-foot">
        已有账号？
        <router-link to="/login">直接登录</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { register } from '../api/auth'

const router = useRouter()
const formRef = ref()
const submitting = ref(false)

const form = reactive({
  studentNo: '',
  realName: '',
  password: '',
  confirmPassword: '',
  mobile: '',
})

/** 校验规则与后端 RegisterDTO 逐条对齐（文档表 2-4） */
const rules = {
  studentNo: [
    { required: true, message: '请输入学号', trigger: 'blur' },
    { pattern: /^\d{8,12}$/, message: '学号为 8-12 位数字', trigger: 'blur' },
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' },
    { max: 20, message: '姓名不超过 20 字', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[A-Za-z])(?=.*\d)\S{8,}$/,
      message: '密码不少于 8 位且须包含字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
  mobile: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号必须为 11 位', trigger: 'blur' },
  ],
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await register({
      studentNo: form.studentNo.trim(),
      realName: form.realName.trim(),
      password: form.password,
      mobile: form.mobile,
    })
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    // 400（学号/手机号已注册）由拦截器统一弹错
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.register-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
  padding: 24px 0;
}
.register-card {
  width: 420px;
  padding: 8px 12px;
}
.register-title {
  text-align: center;
  margin-bottom: 4px;
  color: #303133;
}
.register-sub {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin: 0 0 20px;
}
.register-btn {
  width: 100%;
}
.register-foot {
  text-align: center;
  font-size: 14px;
  color: #909399;
}
</style>
