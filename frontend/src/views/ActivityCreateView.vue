<template>
  <div class="activity-create">
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
        <h2>发布活动</h2>
        <p class="tip">
          公开活动需社联审核通过后才对全校可见；内部活动仅本社团成员可见。
          系统校验：报名截止须早于开始时间、结束须晚于开始、同社团同时段最多一场。
        </p>
      </div>

      <el-card shadow="never" v-loading="loading">
        <el-alert
          v-if="myClubs.length === 0 && !loading"
          title="您还不是任何已成立社团的负责人，无法发布活动"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 16px"
        />

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="110px"
          style="max-width: 680px"
        >
          <el-form-item label="所属社团" prop="clubId">
            <el-select v-model="form.clubId" style="width: 100%" placeholder="选择您负责的社团">
              <el-option
                v-for="c in myClubs"
                :key="c.clubId"
                :label="c.clubName"
                :value="c.clubId"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="活动名称" prop="title">
            <el-input v-model="form.title" maxlength="40" show-word-limit placeholder="如：程序设计之夜" />
          </el-form-item>

          <el-form-item label="活动类型" prop="actType">
            <el-radio-group v-model="form.actType">
              <el-radio :label="1">公开（全校可报名，需审核）</el-radio>
              <el-radio :label="0">内部（仅本社团成员）</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="开始时间" prop="startTime">
            <el-date-picker
              v-model="form.startTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="必须在未来"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="结束时间" prop="endTime">
            <el-date-picker
              v-model="form.endTime"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="须晚于开始时间"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="报名截止" prop="signupDeadline">
            <el-date-picker
              v-model="form.signupDeadline"
              type="datetime"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="须早于开始时间"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="活动地点" prop="location">
            <el-input v-model="form.location" maxlength="80" placeholder="如：第一报告厅" />
          </el-form-item>

          <el-form-item label="名额" prop="capacity">
            <el-input-number v-model="form.capacity" :min="1" :max="500" style="width: 200px" />
            <span class="hint">1 - 500 人</span>
          </el-form-item>

          <el-form-item label="关联场地申请" prop="venueAppId">
            <el-select
              v-model="form.venueAppId"
              clearable
              style="width: 100%"
              placeholder="选填：关联已通过的场地申请单"
            >
              <el-option
                v-for="v in approvedVenueApps"
                :key="v.appId"
                :label="`${v.useDate} ${v.timeSlot} — ${venueName(v.venueId)}`"
                :value="v.appId"
              />
            </el-select>
            <div class="hint">仅列出您已通过的场地申请；也可不占场地稍后补</div>
          </el-form-item>

          <el-form-item label="活动简介" prop="intro">
            <el-input
              v-model="form.intro"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              placeholder="选填"
            />
          </el-form-item>

          <el-form-item>
            <el-button type="primary" :loading="submitting" @click="submit">提交发布</el-button>
            <el-button @click="router.push('/activityList')">取消</el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import request from '../api/request'
import { listMyMemberships } from '../api/membership'
import { myVenueApplications } from '../api/venue'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const myClubs = ref([])          // [{clubId, clubName}] 我是负责人的已成立社团
const approvedVenueApps = ref([]) // status=1 的场地申请
const venuesCache = ref([])      // 场地 id→名称

const formRef = ref(null)
const form = ref({
  clubId: null,
  title: '',
  actType: 1,
  startTime: null,
  endTime: null,
  signupDeadline: null,
  location: '',
  capacity: 50,
  venueAppId: null,
  intro: ''
})

// 时间交叉校验（与后端规则一致）
const validateEndAfterStart = (rule, value, callback) => {
  if (value && form.value.startTime && new Date(value).getTime() <= new Date(form.value.startTime).getTime()) {
    callback(new Error('结束时间必须晚于开始时间'))
  } else callback()
}
const validateDeadlineBeforeStart = (rule, value, callback) => {
  if (value && form.value.startTime && new Date(value).getTime() >= new Date(form.value.startTime).getTime()) {
    callback(new Error('报名截止必须早于开始时间'))
  } else callback()
}

const rules = {
  clubId: [{ required: true, message: '请选择所属社团', trigger: 'change' }],
  title: [
    { required: true, message: '请填写活动名称', trigger: 'blur' },
    { max: 40, message: '不超过 40 字', trigger: 'blur' }
  ],
  actType: [{ required: true, message: '请选择活动类型', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [
    { required: true, message: '请选择结束时间', trigger: 'change' },
    { validator: validateEndAfterStart, trigger: 'change' }
  ],
  signupDeadline: [
    { required: true, message: '请选择报名截止时间', trigger: 'change' },
    { validator: validateDeadlineBeforeStart, trigger: 'change' }
  ],
  location: [
    { required: true, message: '请填写活动地点', trigger: 'blur' },
    { max: 80, message: '不超过 80 字', trigger: 'blur' }
  ],
  capacity: [{ required: true, message: '请填写名额', trigger: 'change' }]
}

function venueName(venueId) {
  const v = venuesCache.value.find(x => x.venueId === venueId)
  return v ? v.venueName : `场地 #${venueId}`
}

async function loadMyClubs() {
  // membership mine 里 role 为 LEADER 且已生效的记录 = 我负责的社团
  const [memRes, clubRes] = await Promise.all([
    listMyMemberships(),
    request.get('/clubs', { params: { page: 1, size: 100 } })
  ])
  const clubNameMap = {}
  for (const c of (clubRes.rows || [])) clubNameMap[c.clubId] = c.clubName
  myClubs.value = (memRes || [])
    .filter(m => m.memberRole === 'LEADER' && m.status === 1)
    .map(m => ({ clubId: m.clubId, clubName: clubNameMap[m.clubId] || `社团 #${m.clubId}` }))
}

async function loadVenueApps() {
  try {
    const res = await myVenueApplications()
    approvedVenueApps.value = (res || []).filter(v => v.status === 1)
    // 拉场地名称（需登录接口，失败则显示编号）
    if (approvedVenueApps.value.length) {
      const vRes = await request.get('/venues', { params: { date: approvedVenueApps.value[0].useDate } })
      venuesCache.value = vRes || []
    }
  } catch (e) { /* 非关键数据 */ }
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const payload = { ...form.value }
    if (!payload.venueAppId) payload.venueAppId = null
    if (!payload.intro) payload.intro = null
    await request.post('/activities', payload)
    ElMessage.success(form.value.actType === 1
      ? '已提交，等待社联审核后对全校可见'
      : '内部活动已发布')
    router.push('/activityList')
  } catch (e) {
    // 409: 同社团同时段已有活动等
  } finally {
    submitting.value = false
  }
}

function logout() {
  userStore.logout()
  router.push('/login')
}

onMounted(async () => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push('/login')
    return
  }
  loading.value = true
  try {
    await Promise.all([loadMyClubs(), loadVenueApps()])
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.activity-create {
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
  margin: 0 32px 0 0;
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
.hint { color: #909399; font-size: 12px; margin-left: 10px; }
</style>
