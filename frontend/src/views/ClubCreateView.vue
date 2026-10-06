<template>
  <div class="club-create">
    <!-- 顶栏 -->
    <header class="topbar">
      <h1 class="logo">校园社团管理</h1>
      <nav class="nav">
        <router-link to="/clubList"><el-button text>社团</el-button></router-link>
        <router-link to="/activityList"><el-button text>活动</el-button></router-link>
        <router-link to="/notice"><el-button text>公告</el-button></router-link>
      </nav>
      <div class="topbar-right">
        <template v-if="userStore.isLogin">
          <span class="welcome">{{ userStore.realName }}</span>
          <el-button text @click="logout">退出</el-button>
        </template>
        <template v-else>
          <router-link to="/login"><el-button text>登录</el-button></router-link>
        </template>
      </div>
    </header>

    <main class="content">
      <div class="page-head">
        <h2>创建社团</h2>
        <p class="tip">提交后进入「待审核」状态，由社联管理员审核通过后正式成立（用例 bu_创建社团）</p>
      </div>

      <el-card shadow="never">
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="100px"
          style="max-width: 640px"
        >
          <el-form-item label="社团名称" prop="clubName">
            <el-input
              v-model="form.clubName"
              maxlength="40"
              show-word-limit
              placeholder="如：摄影协会"
            />
          </el-form-item>

          <el-form-item label="社团类别" prop="category">
            <el-select v-model="form.category" style="width: 100%" placeholder="选择或输入类别">
              <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>

          <el-form-item label="指导老师" prop="advisor">
            <el-input v-model="form.advisor" maxlength="20" placeholder="选填" />
          </el-form-item>

          <el-form-item label="纳新截止" prop="recruitDeadline">
            <el-date-picker
              v-model="form.recruitDeadline"
              type="datetime"
              placeholder="留空 = 长期纳新"
              format="YYYY-MM-DD HH:mm:ss"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="社团简介" prop="intro">
            <el-input
              v-model="form.intro"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="选填，一句话介绍社团"
            />
          </el-form-item>

          <el-form-item label="社团章程" prop="charter">
            <el-input
              v-model="form.charter"
              type="textarea"
              :rows="6"
              placeholder="选填。文档要求创建时提交章程，建议填写以提高审核通过率"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">提交审核</el-button>
            <el-button @click="router.push('/clubList')">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import request from '../api/request'

const router = useRouter()
const userStore = useUserStore()

const CATEGORIES = ['学术', '文艺', '体育', '实践', '公益', '科技', '其他']

const formRef = ref(null)
const submitting = ref(false)
const form = ref({
  clubName: '',
  category: '',
  advisor: '',
  recruitDeadline: null,
  intro: '',
  charter: ''
})

const rules = {
  clubName: [
    { required: true, message: '请输入社团名称', trigger: 'blur' },
    { max: 40, message: '不超过 40 字', trigger: 'blur' }
  ],
  category: [{ required: true, message: '请选择社团类别', trigger: 'change' }],
  advisor: [{ max: 20, message: '不超过 20 字', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await request.post('/clubs', {
      clubName: form.value.clubName,
      category: form.value.category,
      advisor: form.value.advisor || null,
      recruitDeadline: form.value.recruitDeadline || null,
      intro: form.value.intro || null,
      charter: form.value.charter || null
    })
    ElMessage.success('已提交，等待社联审核')
    router.push('/clubList')
  } catch (e) {
    // 统一错误处理（403 非 LEADER / 409 重名等）
  } finally {
    submitting.value = false
  }
}

function logout() {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
  }
})
</script>

<style scoped>
.club-create {
  min-height: 100vh;
  background: #f5f7fa;
}
.topbar {
  display: flex;
  align-items: center;
  gap: 24px;
  background: #fff;
  padding: 0 24px;
  height: 56px;
  border-bottom: 1px solid #e4e7ed;
}
.logo {
  font-size: 18px;
  margin: 0;
  color: #409eff;
}
.nav { display: flex; gap: 4px; }
.topbar-right { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.welcome { color: #606266; font-size: 14px; }
.content {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}
.page-head h2 { margin: 0 0 4px; }
.tip { color: #909399; font-size: 13px; margin: 0 0 16px; }
</style>
