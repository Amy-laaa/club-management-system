<template>
  <div class="activity-list-page">
    <header class="topbar">
      <div class="topbar-inner">
        <h1 class="logo">校园活动</h1>
        <nav class="nav">
          <router-link to="/clubList">社团</router-link>
          <router-link to="/activityList" class="active">活动</router-link>
        </nav>
        <div class="topbar-right">
          <template v-if="userStore.isLogin">
            <span class="welcome">{{ userStore.realName }}</span>
            <router-link to="/myRegistrations">
              <el-button text>我的报名</el-button>
            </router-link>
            <router-link v-if="userStore.role === 'LEADER'" to="/checkIn">
              <el-button text>签到核销</el-button>
            </router-link>
            <el-button text @click="logout">退出</el-button>
          </template>
          <template v-else>
            <el-button text @click="$router.push('/login')">登录</el-button>
            <el-button text @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <main class="main">
      <!-- 筛选工具条 -->
      <div class="toolbar">
        <el-select
          v-model="actType"
          placeholder="全部类型"
          clearable
          class="type-select"
          @change="handleFilter"
        >
          <el-option label="公开活动" :value="1" />
          <el-option label="社团内部" :value="0" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索活动名称"
          clearable
          class="search-input"
          @keyup.enter="handleFilter"
          @clear="handleFilter"
        >
          <template #append>
            <el-button @click="handleFilter">搜索</el-button>
          </template>
        </el-input>
        <el-tag type="info" effect="plain">共 {{ total }} 个活动</el-tag>
      </div>

      <!-- 活动卡片 -->
      <div v-loading="loading" class="card-grid">
        <el-card
          v-for="act in activities"
          :key="act.activityId"
          class="act-card"
          shadow="hover"
          @click="goDetail(act.activityId)"
        >
          <div class="card-head">
            <el-tag :type="actTypeTag(act.actType)" size="small" effect="light">
              {{ act.actType === 1 ? '公开' : '内部' }}
            </el-tag>
            <el-tag
              :type="statusTag(act.status).type"
              size="small"
              effect="plain"
            >
              {{ statusTag(act.status).text }}
            </el-tag>
          </div>
          <h3 class="act-title">{{ act.title }}</h3>
          <p class="act-club">{{ clubNameOf(act.clubId) }}</p>
          <div class="act-info">
            <span>🕐 {{ formatTime(act.startTime) }}</span>
            <span>📍 {{ act.location }}</span>
          </div>
          <div class="card-foot">
            <span
              class="remain"
              :class="{ full: act.remain === 0, danger: act.remain > 0 && act.remain <= 3 }"
            >
              {{ act.remain === 0 ? '已满员 / 候补' : `剩余名额 ${act.remain}` }}
            </span>
            <span class="deadline" v-if="act.signupDeadline">
              截止 {{ formatTime(act.signupDeadline).slice(0, 10) }}
            </span>
          </div>
        </el-card>

        <el-empty
          v-if="!loading && activities.length === 0"
          description="没有符合条件的活动"
          class="empty-block"
        />
      </div>

      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="size"
          :total="total"
          :page-sizes="[9, 18, 27]"
          layout="total, sizes, prev, pager, next"
          @current-change="load"
          @size-change="handleSizeChange"
        />
      </div>
    </main>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listActivities } from '../api/activity'
import { listClubs } from '../api/club'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const activities = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(9)
const keyword = ref('')
const actType = ref(null)
const loading = ref(false)
/** clubId -> clubName 映射（接口 rows 不带社团名，前端补） */
const clubMap = ref({})

async function load() {
  loading.value = true
  try {
    const data = await listActivities({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
      actType: actType.value ?? undefined,
    })
    activities.value = data.rows || []
    total.value = data.total || 0
  } catch (e) {
    // 拦截器已统一弹错
  } finally {
    loading.value = false
  }
}

/** 加载全部社团建映射（社团量级小，一次拉 50 个够用） */
async function loadClubMap() {
  try {
    const data = await listClubs({ page: 1, size: 50 })
    const map = {}
    ;(data.rows || []).forEach((c) => (map[c.clubId] = c.clubName))
    clubMap.value = map
  } catch (e) {
    clubMap.value = {}
  }
}

function clubNameOf(clubId) {
  return clubMap.value[clubId] || '社团'
}

function handleFilter() {
  page.value = 1
  load()
}

function handleSizeChange() {
  page.value = 1
  load()
}

function goDetail(id) {
  router.push({ path: '/activityDetail', query: { id } })
}

function logout() {
  userStore.clear()
  router.push('/login')
}

function formatTime(t) {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

/** 活动状态: 0待审核 1已发布 2进行中 3已结束 4已取消（文档表 2-21） */
function statusTag(status) {
  const map = {
    0: { type: 'info', text: '待审核' },
    1: { type: 'primary', text: '报名中' },
    2: { type: 'success', text: '进行中' },
    3: { type: 'info', text: '已结束' },
    4: { type: 'danger', text: '已取消' },
  }
  return map[status] ?? { type: 'info', text: '未知' }
}

function actTypeTag(type) {
  return type === 1 ? 'warning' : ''
}

onMounted(() => {
  load()
  loadClubMap()
})
</script>

<style scoped>
.activity-list-page {
  min-height: 100vh;
  background: #f0f2f5;
}
.topbar {
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  position: sticky;
  top: 0;
  z-index: 10;
}
.topbar-inner {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 24px;
  height: 56px;
  display: flex;
  align-items: center;
  gap: 24px;
}
.logo {
  font-size: 18px;
  margin: 0;
  color: #409eff;
}
.nav {
  display: flex;
  gap: 16px;
  flex: 1;
}
.nav a {
  color: #606266;
  text-decoration: none;
  font-size: 14px;
  padding: 4px 0;
  border-bottom: 2px solid transparent;
}
.nav a.active,
.nav a:hover {
  color: #409eff;
  border-bottom-color: #409eff;
}
.welcome {
  margin-right: 12px;
  color: #606266;
}

.main {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
.type-select {
  width: 140px;
}
.search-input {
  width: 300px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  min-height: 200px;
}
@media (max-width: 900px) {
  .card-grid {
    grid-template-columns: 1fr;
  }
}
.act-card {
  cursor: pointer;
}
.act-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 170px;
}
.card-head {
  display: flex;
  gap: 8px;
}
.act-title {
  margin: 10px 0 4px;
  font-size: 16px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.act-club {
  margin: 0 0 10px;
  font-size: 13px;
  color: #909399;
}
.act-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}
.card-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #c0c4cc;
}
.remain {
  color: #67c23a;
}
.remain.danger {
  color: #e6a23c;
}
.remain.full {
  color: #f56c6c;
}
.empty-block {
  grid-column: 1 / -1;
}
.pager {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}
</style>
