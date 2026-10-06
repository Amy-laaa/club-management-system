<template>
  <div class="notice">
    <!-- 顶栏 -->
    <header class="topbar">
      <h1 class="logo">校园社团管理</h1>
      <nav class="nav">
        <router-link to="/clubList"><el-button text>社团</el-button></router-link>
        <router-link to="/activityList"><el-button text>活动</el-button></router-link>
        <router-link to="/notice"><el-button text>公告</el-button></router-link>
        <router-link v-if="isAdmin" to="/stats"><el-button text>数据统计</el-button></router-link>
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
        <div>
          <h2>公告中心</h2>
          <p class="tip">校级公告与社团公告（文档表 2-9 的 notice 页面，对接契约 #22 #23）</p>
        </div>
        <el-button v-if="canPublish" type="primary" @click="openPublish">发布公告</el-button>
      </div>

      <el-tabs v-model="scope" @tab-change="onTabChange">
        <el-tab-pane label="全校公告" name="1" />
        <el-tab-pane label="社团公告" name="0" />
      </el-tabs>

      <!-- 社团公告：选择社团（我加入的社团；管理员可选全部） -->
      <div v-if="scope === '0'" class="club-picker">
        <span class="picker-label">社团：</span>
        <el-select v-model="clubId" style="width: 280px" placeholder="选择社团" @change="reload">
          <el-option v-for="c in clubOptions" :key="c.clubId" :label="c.clubName || `社团 #${c.clubId}`" :value="c.clubId" />
        </el-select>
        <span v-if="clubOptions.length === 0" class="picker-empty">您尚未加入任何社团，暂无社团公告可查看</span>
      </div>

      <!-- 公告列表 -->
      <el-table :data="rows" v-loading="loading" border>
        <el-table-column label=" " width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPinned === 1" type="danger" size="small" effect="dark">置顶</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="220">
          <template #default="{ row }">
            <el-link type="primary" @click="openDetail(row)">{{ row.title }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="160">
          <template #default="{ row }">
            <el-tag v-if="row.scope === 1" size="small">校级</el-tag>
            <el-tag v-else type="success" size="small">{{ row.clubName || '社团' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publisherName" label="发布人" width="110" />
        <el-table-column label="发布时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="180" align="center">
          <template #default="{ row }">
            <el-button size="small" text type="primary" @click="togglePin(row)">
              {{ row.isPinned === 1 ? '取消置顶' : '置顶' }}
            </el-button>
            <el-button size="small" text type="danger" @click="doWithdraw(row)">撤回</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="total > size"
        class="pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />

      <!-- 公告详情 -->
      <el-dialog v-model="detailVisible" :title="detail.title" width="560px">
        <div class="detail-meta">
          <el-tag v-if="detail.isPinned === 1" type="danger" size="small">置顶</el-tag>
          <el-tag v-if="detail.scope === 1" size="small">校级公告</el-tag>
          <el-tag v-else type="success" size="small">{{ detail.clubName || '社团公告' }}</el-tag>
          <span v-if="detail.status === 0"><el-tag type="info" size="small">已撤回</el-tag></span>
          <span class="meta-text">{{ detail.publisherName }} · {{ fmtTime(detail.createdAt) }}</span>
        </div>
        <div class="detail-content">{{ detail.content }}</div>
      </el-dialog>

      <!-- 发布公告 -->
      <el-dialog v-model="publishVisible" title="发布公告" width="520px">
        <el-alert v-if="isAdmin" type="info" :closable="false" style="margin-bottom: 12px"
          title="社联/系统管理员发布的是全校公告（校级置顶同时最多 3 条）" />
        <el-alert v-else type="info" :closable="false" style="margin-bottom: 12px"
          title="社长发布的是本社团公告，仅本社团成员可见" />
        <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
          <el-form-item label="标题" prop="title">
            <el-input v-model="form.title" maxlength="40" show-word-limit placeholder="不超过 40 个字" />
          </el-form-item>
          <el-form-item label="正文" prop="content">
            <el-input v-model="form.content" type="textarea" :rows="5" placeholder="公告正文" />
          </el-form-item>
          <el-form-item label="置顶">
            <el-switch v-model="form.isPinned" :active-value="1" :inactive-value="0" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="publishVisible = false">取消</el-button>
          <el-button type="primary" :loading="publishing" @click="doPublish">发布</el-button>
        </template>
      </el-dialog>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listNotices, getNotice, publishNotice, pinNotice, withdrawNotice } from '../api/notice'
import { listMyMemberships } from '../api/membership'
import { listClubs } from '../api/club'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const isAdmin = computed(() => ['UNION_ADMIN', 'SYS_ADMIN'].includes(userStore.role))
const isLeader = ref(false)
const canPublish = computed(() => isAdmin.value || isLeader.value)
const canManage = computed(() => isAdmin.value || isLeader.value)

// 列表状态
const scope = ref('1')
const clubId = ref(null)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)
const loading = ref(false)
const clubOptions = ref([])

// 详情
const detailVisible = ref(false)
const detail = ref({})

// 发布
const publishVisible = ref(false)
const publishing = ref(false)
const formRef = ref(null)
const form = ref({ title: '', content: '', isPinned: 0 })
const rules = {
  title: [
    { required: true, message: '请输入标题', trigger: 'blur' },
    { max: 40, message: '标题不能超过 40 个字', trigger: 'blur' },
  ],
  content: [{ required: true, message: '请输入正文', trigger: 'blur' }],
}

function fmtTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

async function loadList() {
  loading.value = true
  try {
    const params = { scope: scope.value, page: page.value, size: size.value }
    if (scope.value === '0') {
      if (!clubId.value) {
        rows.value = []
        total.value = 0
        return
      }
      params.clubId = clubId.value
    }
    const data = await listNotices(params)
    rows.value = data.rows || data.list || []
    total.value = data.total || 0
  } catch (e) {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  loadList()
}

function onPage(p) {
  page.value = p
  loadList()
}

function onTabChange() {
  reload()
}

async function openDetail(row) {
  try {
    detail.value = await getNotice(row.noticeId)
    detailVisible.value = true
  } catch (e) {
    // 详情无权限/已撤回等错误由拦截器统一提示
  }
}

function openPublish() {
  form.value = { title: '', content: '', isPinned: 0 }
  publishVisible.value = true
}

async function doPublish() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  publishing.value = true
  try {
    await publishNotice({ title: form.value.title, content: form.value.content, isPinned: form.value.isPinned })
    ElMessage.success('发布成功')
    publishVisible.value = false
    reload()
  } catch (e) {
    // 错误提示由拦截器统一处理
  } finally {
    publishing.value = false
  }
}

async function togglePin(row) {
  try {
    await pinNotice(row.noticeId)          // 不传值 → 按当前状态取反
    ElMessage.success(row.isPinned === 1 ? '已取消置顶' : '已置顶')
    loadList()
  } catch (e) {
    // 置顶上限(409)等错误由拦截器统一提示
  }
}

async function doWithdraw(row) {
  try {
    await ElMessageBox.confirm(`确定撤回公告「${row.title}」？撤回后不再展示`, '撤回确认', { type: 'warning' })
  } catch (e) {
    return
  }
  try {
    await withdrawNotice(row.noticeId)
    ElMessage.success('已撤回')
    loadList()
  } catch (e) {
    // 错误提示由拦截器统一处理
  }
}

function logout() {
  userStore.logout()
  router.push('/login')
}

onMounted(async () => {
  if (!userStore.isLogin) {
    ElMessage.warning('请先登录')
    router.push({ path: '/login', query: { redirect: '/notice' } })
    return
  }
  // 我加入的社团（含我作为负责人的社团）→ 社团公告 tab 的下拉选项
  try {
    const list = (await listMyMemberships()) || []
    const joined = list.filter((m) => m.status === 1)
    isLeader.value = joined.some((m) => m.memberRole === 'LEADER')
    if (joined.length) {
      clubOptions.value = joined
      clubId.value = joined[0].clubId
    } else if (isAdmin.value) {
      // 管理员没加入任何社团时，提供全部社团下拉
      const data = await listClubs({ page: 1, size: 50 })
      clubOptions.value = data.rows || []
    }
  } catch (e) {
    // 忽略：全校公告 tab 不依赖它
  }
  loadList()
})
</script>

<style scoped>
.notice {
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
  margin-right: 32px;
  font-size: 18px;
  margin: 0;
  color: #409eff;
}
.nav { display: flex; gap: 4px; }
.topbar-right { margin-left: auto; display: flex; align-items: center; gap: 8px; }
.welcome { color: #606266; font-size: 14px; }
.content {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 12px;
}
.page-head h2 { margin: 0 0 4px; }
.tip { color: #909399; font-size: 13px; margin: 0; }
.club-picker {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}
.picker-label { color: #606266; font-size: 14px; }
.picker-empty { color: #909399; font-size: 13px; }
.pager {
  margin-top: 16px;
  justify-content: flex-end;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.meta-text { color: #909399; font-size: 13px; }
.detail-content {
  font-size: 14px;
  color: #303133;
  line-height: 1.9;
  white-space: pre-wrap;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 16px;
}

/* 响应式：<768px（文档要求） */
@media (max-width: 768px) {
  .content { padding: 16px 12px; }
  .topbar { padding: 0 12px; gap: 12px; }
  .page-head { flex-direction: column; gap: 8px; }
}
</style>
