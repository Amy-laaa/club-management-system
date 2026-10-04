<template>
  <div class="login-page">
    <el-card class="login-card">
      <h2 class="login-title">校园社团综合管理系统</h2>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        size="large"
        @keyup.enter="submit"
      >
        <el-form-item label="学号 / 手机号" prop="account">
          <el-input
            v-model="form.account"
            placeholder="请输入学号或手机号"
            clearable
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            class="login-btn"
            :loading="loading"
            @click="submit"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-foot">
        还没有账号？
        <router-link to="/register">立即注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '../api/auth'
import { useUserStore } from '../stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({
  account: '',
  password: '',
})

const rules = {
  account: [{ required: true, message: '请输入学号或手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    // 后端返回 { token, userId, realName, roleCode }
    const data = await login({ account: form.account, password: form.password })
    userStore.setLogin(data)
    ElMessage.success(`欢迎回来，${data.realName}`)
    // 登录后回到原目标页，默认去社团列表
    router.push(route.query.redirect || '/clubList')
  } catch (e) {
    // 错误提示已由 axios 拦截器统一弹出
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}
.login-card {
  width: 380px;
  padding: 8px 12px;
}
.login-title {
  text-align: center;
  margin-bottom: 24px;
  color: #303133;
}
.login-btn {
  width: 100%;
}
.login-foot {
  text-align: center;
  font-size: 14px;
  color: #909399;
}
</style>
