<template>
  <div class="venue-apply">
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
        <h2>场地申请</h2>
        <p class="tip">选择日期查看各场地时段占用情况（灰色为已占用/待审核），点击空闲格子提交申请</p>
      </div>

      <!-- 日期选择 -->
      <div class="filter-bar">
        <span class="label">使用日期：</span>
        <el-date-picker
          v-model="selectedDate"
          type="date"
          placeholder="选择日期"
          format="YYYY-MM-DD"
          value-format="YYYY-MM-DD"
          :disabled-date="disablePast"
          style="width: 200px"
          @change="loadVenues"
        />
        <el-button :loading="loading" style="margin-left: 12px" @click="loadVenues">刷新占用</el-button>
      </div>

      <!-- 场地 × 时段占用矩阵（文档图 2-10） -->
      <el-table :data="venues" border style="width: 100%" v-loading="loading">
        <el-table-column label="场地 / 时段" min-width="180">
          <template #default="{ row }">
            <div class="venue-name">{{ row.venueName }}</div>
            <div class="venue-capacity">容量 {{ row.capacity }} 人</div>
          </template>
        </el-table-column>
        <el-table-column
          v-for="slot in TIME_SLOTS"
          :key="slot"
          :label="slot"
          min-width="130"
          align="center"
        >
          <template #default="{ row }">
            <div
              class="slot-cell"
              :class="slotState(row, slot).cls"
              @click="onSlotClick(row, slot)"
            >
              <template v-if="slotState(row, slot).occupied">
                {{ slotState(row, slot).text }}
              </template>
              <template v-else-if="isLeader">
                可申请
              </template>
              <template v-else>空闲</template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-alert
        v-if="!userStore.isLogin"
        title="请先登录后查看场地占用详情"
        type="info"
        show-icon
        :closable="false"
        style="margin-top: 16px"
      />
      <el-alert
        v-else-if="!isLeader"
        title="场地申请仅社团负责人可操作，您可查看各场地占用情况"
        type="info"
        show-icon
        :closable="false"
        style="margin-top: 16px"
      />

      <!-- 我的场地申请 -->
      <div v-if="userStore.isLogin" class="mine-section">
        <h3>我的场地申请</h3>
        <el-table :data="mine" border style="width: 100%">
          <el-table-column prop="useDate" label="使用日期" width="110" />
          <el-table-column prop="timeSlot" label="时段" width="120" />
          <el-table-column label="场地" min-width="180">
            <template #default="{ row }">{{ venueName(row.venueId) }}</template>
          </el-table-column>
          <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="statusTag(row.status).type" size="small">
                {{ statusTag(row.status).text }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="auditRemark" label="审批意见" min-width="160" show-overflow-tooltip />
        </el-table>
      </div>
    </main>

    <!-- 申请弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="场地">
          <span>{{ applyVenueInfo.venueName }}</span>
        </el-form-item>
        <el-form-item label="日期时段">
          <span>{{ applyVenueInfo.date }} {{ applyVenueInfo.slot }}</span>
        </el-form-item>
        <el-form-item label="关联活动">
          <el-select v-model="form.activityId" clearable placeholder="可先占场地，暂不关联" style="width: 100%">
            <el-option
              v-for="a in myClubActivities"
              :key="a.activityId"
              :label="a.title"
              :value="a.activityId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="用途" prop="purpose">
          <el-input
            v-model="form.purpose"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="如：计算机协会程序设计之夜"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitApply">提交申请</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '../stores/user'
import { listVenues, applyVenue, myVenueApplications } from '../api/venue'
import { listActivities } from '../api/activity'

const router = useRouter()
const userStore = useUserStore()

// 标准时段集合（覆盖 demo 数据与常见申请时段）
const TIME_SLOTS = ['08:00-10:00', '10:00-12:00', '14:00-16:00', '16:00-18:00', '18:00-21:00', '19:00-21:00']

const today = new Date().toISOString().slice(0, 10)
const selectedDate = ref(today)
const venues = ref([])
const mine = ref([])
const myClubActivities = ref([])
const loading = ref(false)

const isLeader = computed(() => userStore.roleCode === 'LEADER')

const dialogVisible = ref(false)
const submitting = ref(false)
const applyVenueInfo = ref({ venueId: null, venueName: '', date: '', slot: '' })
const formRef = ref(null)
const form = ref({ activityId: null, purpose: '' })
const rules = {
  purpose: [{ required: true, message: '请填写活动用途', trigger: 'blur' }]
}

const dialogTitle = computed(() => `申请场地 — ${applyVenueInfo.value.slot}`)

function disablePast(d) {
  const t = new Date()
  t.setHours(0, 0, 0, 0)
  return d.getTime() < t.getTime()
}

// 解析 occupiedSlots（后端格式如 "18:00-21:00(已通过)"）
function slotState(row, slot) {
  const occ = (row.occupiedSlots || []).find(s => s.split('(')[0] === slot)
  if (!occ) return { occupied: false, cls: 'free', text: '' }
  const label = occ.includes('已通过') ? '已通过' : '待审'
  return { occupied: true, cls: label === '已通过' ? 'blocked' : 'pending', text: label }
}

function onSlotClick(row, slot) {
  const st = slotState(row, slot)
  if (st.occupied) {
    ElMessage.info(`${row.venueName} ${selectedDate.value} ${slot} ${st.text === '已通过' ? '已被占用' : '存在待审申请'}`)
    return
  }
  if (!isLeader.value) {
    ElMessage.warning('仅社团负责人可申请场地')
    return
  }
  applyVenueInfo.value = { venueId: row.venueId, venueName: row.venueName, date: selectedDate.value, slot }
  form.value = { activityId: null, purpose: '' }
  dialogVisible.value = true
}

async function submitApply() {
  await formRef.value.validate()
  submitting.value = true
  try {
    await applyVenue({
      venueId: applyVenueInfo.value.venueId,
      activityId: form.value.activityId || null,
      useDate: applyVenueInfo.value.date,
      timeSlot: applyVenueInfo.value.slot,
      purpose: form.value.purpose
    })
    dialogVisible.value = false
    ElMessage.success('申请已提交，等待社联审核')
    await Promise.all([loadVenues(), loadMine()])
  } catch (e) {
    // request.js 已统一弹错（409 时段冲突等）
  } finally {
    submitting.value = false
  }
}

function venueName(venueId) {
  const v = venues.value.find(x => x.venueId === venueId)
  return v ? v.venueName : `场地 #${venueId}`
}

function statusTag(status) {
  const map = {
    0: { text: '待审核', type: 'warning' },
    1: { text: '已通过', type: 'success' },
    2: { text: '已驳回', type: 'danger' },
    3: { text: '已释放', type: 'info' }
  }
  return map[status] || { text: '未知', type: 'info' }
}

async function loadVenues() {
  if (!userStore.isLogin) return
  loading.value = true
  try {
    const res = await listVenues(selectedDate.value)
    venues.value = res || []
  } catch (e) { /* 统一处理 */ } finally {
    loading.value = false
  }
}

async function loadMine() {
  if (!userStore.isLogin) return
  try {
    const res = await myVenueApplications()
    mine.value = res || []
  } catch (e) { /* 统一处理 */ }
}

async function loadMyActivities() {
  if (!isLeader.value) return
  try {
    // 取自己社团已发布的活动供关联（后端 rows 为活动实体，含 clubId）
    const res = await listActivities({ page: 1, size: 50 })
    myClubActivities.value = (res.rows || [])
  } catch (e) { /* 忽略 */ }
}

function logout() {
  userStore.logout()
  ElMessage.success('已退出登录')
  router.push('/login')
}

onMounted(() => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录后查看场地信息')
    router.push('/login')
    return
  }
  loadVenues()
  loadMine()
  loadMyActivities()
})
</script>

<style scoped>
.venue-apply {
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.page-head h2 { margin: 0 0 4px; }
.tip { color: #909399; font-size: 13px; margin: 0 0 16px; }
.filter-bar {
  display: flex;
  align-items: center;
  background: #fff;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 16px;
}
.label { font-size: 14px; color: #606266; }
.slot-cell {
  border-radius: 6px;
  padding: 10px 4px;
  font-size: 13px;
  cursor: pointer;
  user-select: none;
  transition: all .15s;
}
.slot-cell.free {
  background: #f0f9eb;
  color: #67c23a;
  border: 1px dashed #b3e19d;
}
.slot-cell.free:hover { background: #e1f3d8; }
.slot-cell.blocked {
  background: #f4f4f5;
  color: #909399;
  border: 1px solid #e9e9eb;
  cursor: not-allowed;
}
.slot-cell.pending {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #faecd8;
}
.venue-name { font-weight: 600; color: #303133; }
.venue-capacity { font-size: 12px; color: #909399; margin-top: 2px; }
.mine-section { margin-top: 24px; }
.mine-section h3 { margin: 0 0 12px; }
</style>
