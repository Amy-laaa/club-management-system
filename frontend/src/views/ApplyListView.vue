<template>
  <div class="apply-list">
    <!-- 顶栏 -->
    <header class="topbar">
      <h1 class="logo">校园社团管理</h1>
      <nav class="nav">
        <router-link to="/clubList"><el-button text>社团</el-button></router-link>
        <router-link to="/activityList"><el-button text>活动</el-button></router-link>
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
        <h2>审批工作台</h2>
        <p class="tip">社联管理员统一审核：社团成立 / 公开活动发布 / 场地申请。审批结果写入审计日志</p>
      </div>

      <el-alert
        v-if="!isAdmin"
        title="此页面为社联管理员专用，您无权审批（可查看演示请用社联账号登录）"
        type="warning"
        show-icon
        :closable="false"
        style="margin-bottom: 16px"
      />

      <el-tabs v-model="activeTab" @tab-change="loadCurrent">
        <!-- ============ 社团审核 ============ -->
        <el-tab-pane label="社团成立" name="club">
          <el-table :data="clubRows" border v-loading="clubLoading">
            <el-table-column prop="clubId" label="编号" width="70" />
            <el-table-column prop="clubName" label="社团名称" min-width="140" />
            <el-table-column prop="category" label="类别" width="80" />
            <el-table-column prop="advisor" label="指导老师" width="100" />
            <el-table-column prop="intro" label="简介" min-width="200" show-overflow-tooltip />
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="success" :disabled="!isAdmin" @click="openAudit('club', row)">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              layout="prev, pager, next, total"
              :total="clubTotal"
              :page-size="PAGE_SIZE"
              :current-page="clubPage"
              @current-change="p => { clubPage = p; loadTab('club') }"
            />
          </div>
        </el-tab-pane>

        <!-- ============ 活动审核 ============ -->
        <el-tab-pane label="公开活动" name="activity">
          <el-table :data="activityRows" border v-loading="activityLoading">
            <el-table-column prop="activityId" label="编号" width="70" />
            <el-table-column prop="title" label="活动名称" min-width="160" />
            <el-table-column label="社团" width="120">
              <template #default="{ row }">{{ clubName(row.clubId) }}</template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.startTime) }}</template>
            </el-table-column>
            <el-table-column prop="location" label="地点" min-width="140" show-overflow-tooltip />
            <el-table-column prop="capacity" label="名额" width="70" />
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="success" :disabled="!isAdmin" @click="openAudit('activity', row)">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              layout="prev, pager, next, total"
              :total="activityTotal"
              :page-size="PAGE_SIZE"
              :current-page="activityPage"
              @current-change="p => { activityPage = p; loadTab('activity') }"
            />
          </div>
        </el-tab-pane>

        <!-- ============ 场地审核 ============ -->
        <el-tab-pane label="场地申请" name="venue">
          <el-table :data="venueRows" border v-loading="venueLoading">
            <el-table-column prop="appId" label="编号" width="70" />
            <el-table-column label="场地" width="160">
              <template #default="{ row }">{{ venueName(row.venueId) }}</template>
            </el-table-column>
            <el-table-column prop="useDate" label="使用日期" width="110" />
            <el-table-column prop="timeSlot" label="时段" width="120" />
            <el-table-column prop="purpose" label="用途" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="160" align="center" fixed="right">
              <template #default="{ row }">
                <el-button size="small" type="success" :disabled="!isAdmin" @click="openAudit('venue', row)">审批</el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              layout="prev, pager, next, total"
              :total="venueTotal"
              :page-size="PAGE_SIZE"
              :current-page="venuePage"
              @current-change="p => { venuePage = p; loadTab('venue') }"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </main>

    <!-- 审批弹窗 -->
    <el-dialog v-model="dialogVisible" title="审批" width="460px">
      <div class="audit-summary">{{ auditSummary }}</div>
      <el-form label-width="80px" style="margin-top: 12px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="auditForm.result">
            <el-radio :label="1">通过</el-radio>
            <el-radio :label="0">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input
            v-model="auditForm.remark"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            :placeholder="auditForm.result === 0 ? '驳回时必填原因（通知申请人）' : '选填，如：同意，注意用电安全'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitAudit">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'
import {
  listPendingClubs, listPendingActivities, listPendingVenueApps,
  auditClub, auditActivity, auditVenueApp
} from '../api/admin'
import { listClubs } from '../api/club'
import request from '../api/request'

const router = useRouter()
const userStore = useUserStore()

const PAGE_SIZE = 10
const isAdmin = computed(() => ['UNION_ADMIN', 'SYS_ADMIN'].includes(userStore.role))

// 三个 tab 各自的数据
const activeTab = ref('club')
const clubRows = ref([]); const clubTotal = ref(0); const clubPage = ref(1); const clubLoading = ref(false)
const activityRows = ref([]); const activityTotal = ref(0); const activityPage = ref(1); const activityLoading = ref(false)
const venueRows = ref([]); const venueTotal = ref(0); const venuePage = ref(1); const venueLoading = ref(false)

// 名称映射缓存
const clubsCache = ref([])
const venuesCache = ref([])

// 审批弹窗
const dialogVisible = ref(false)
const submitting = ref(false)
const auditForm = ref({ result: 1, remark: '' })
const current = ref(null)   // { type, id, summary }

const auditSummary = computed(() => current.value ? current.value.summary : '')

function clubName(clubId) {
  const c = clubsCache.value.find(x => x.clubId === clubId)
  return c ? c.clubName : `社团 #${clubId}`
}

function venueName(venueId) {
  const v = venuesCache.value.find(x => x.venueId === venueId)
  return v ? v.venueName : `场地 #${venueId}`
}

function fmtTime(t) {
  return (t || '').replace('T', ' ').slice(0, 16)
}

async function loadTab(tab) {
  const p = { page: 1, size: PAGE_SIZE }
  try {
    if (tab === 'club') {
      clubLoading.value = true
      const res = await listPendingClubs({ page: clubPage.value, size: PAGE_SIZE })
      clubRows.value = res.rows || []
      clubTotal.value = res.total || 0
    } else if (tab === 'activity') {
      activityLoading.value = true
      const res = await listPendingActivities({ page: activityPage.value, size: PAGE_SIZE })
      activityRows.value = res.rows || []
      activityTotal.value = res.total || 0
    } else if (tab === 'venue') {
      venueLoading.value = true
      const res = await listPendingVenueApps({ page: venuePage.value, size: PAGE_SIZE })
      venueRows.value = res.rows || []
      venueTotal.value = res.total || 0
    }
  } catch (e) {
    // 403 非 admin 由统一拦截提示
  } finally {
    clubLoading.value = activityLoading.value = venueLoading.value = false
  }
}

function loadCurrent() {
  loadTab(activeTab.value)
}

function openAudit(type, row) {
  let summary = ''
  if (type === 'club') summary = `社团「${row.clubName}」（${row.category}，指导老师：${row.advisor || '无'}）`
  if (type === 'activity') summary = `活动「${row.title}」${fmtTime(row.startTime)} @ ${row.location}（名额 ${row.capacity}）`
  if (type === 'venue') summary = `场地「${venueName(row.venueId)}」${row.useDate} ${row.timeSlot} — ${row.purpose}`
  current.value = { type, id: type === 'club' ? row.clubId : type === 'activity' ? row.activityId : row.appId, summary }
  auditForm.value = { result: 1, remark: '' }
  dialogVisible.value = true
}

async function submitAudit() {
  if (auditForm.value.result === 0 && !auditForm.value.remark.trim()) {
    ElMessage.warning('驳回时请填写原因')
    return
  }
  const { type, id } = current.value
  submitting.value = true
  try {
    const body = { result: auditForm.value.result, remark: auditForm.value.remark || null }
    if (type === 'club') await auditClub(id, body)
    else if (type === 'activity') await auditActivity(id, body)
    else await auditVenueApp(id, body)
    dialogVisible.value = false
    ElMessage.success(auditForm.value.result === 1 ? '已通过' : '已驳回')
    loadTab(type)
  } catch (e) {
    // 409 场地冲突等统一提示
  } finally {
    submitting.value = false
  }
}

async function loadCaches() {
  try {
    const res = await listClubs({ page: 1, size: 100 })
    clubsCache.value = res.rows || []
  } catch (e) { /* 忽略 */ }
  try {
    const today = new Date().toISOString().slice(0, 10)
    const res = await request.get('/venues', { params: { date: today } })
    venuesCache.value = res || []
  } catch (e) { /* 忽略 */ }
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
  await Promise.all([loadTab('club'), loadCaches()])
})
</script>

<style scoped>
.apply-list {
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
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px;
}
.page-head h2 { margin: 0 0 4px; }
.tip { color: #909399; font-size: 13px; margin: 0 0 16px; }
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.audit-summary {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 10px 12px;
  font-size: 14px;
  color: #303133;
}
</style>
