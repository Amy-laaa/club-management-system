<template>
  <div class="club-list-page">
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="topbar-inner">
        <h1 class="logo">校园社团管理</h1>
        <nav class="nav">
          <router-link to="/clubList" class="active">社团</router-link>
          <router-link to="/activityList">活动</router-link>
        </nav>
        <div class="topbar-right">
          <template v-if="userStore.isLogin">
            <span class="welcome">{{ userStore.realName }}</span>
            <el-button text @click="logout">退出</el-button>
          </template>
          <template v-else>
            <el-button text @click="$router.push('/login')">登录</el-button>
            <el-button text @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- 主体 -->
    <main class="main">
      <div class="toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索社团名称"
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        >
          <template #append>
            <el-button @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
        <el-tag type="info" effect="plain">共 {{ total }} 个社团</el-tag>
      </div>

      <div v-loading="loading" class="card-grid">
        <el-card
          v-for="club in clubs"
          :key="club.clubId"
          class="club-card"
          shadow="hover"
          @click="goDetail(club.clubId)"
        >
          <div class="card-head">
            <span class="club-name">{{ club.clubName }}</span>
            <el-tag :type="categoryTag(club.category)" effect="light">
              {{ club.category }}
            </el-tag>
          </div>
          <p class="club-intro">{{ club.intro || '暂无简介' }}</p>
          <div class="card-foot">
            <span class="advisor" v-if="club.advisor">指导老师：{{ club.advisor }}</span>
            <el-tag v-if="isRecruiting(club)" type="success" size="small" effect="plain">
              纳新中
            </el-tag>
          </div>
        </el-card>

        <el-empty
          v-if="!loading && clubs.length === 0"
          description="没有找到符合条件的社团"
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
import { listClubs } from '../api/club'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()

const clubs = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(9)
const keyword = ref('')
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await listClubs({
      page: page.value,
      size: size.value,
      keyword: keyword.value || undefined,
    })
    clubs.value = data.rows || []
    total.value = data.total || 0
  } catch (e) {
    // 错误提示已由拦截器统一弹出
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function handleSizeChange() {
  page.value = 1
  load()
}

function goDetail(clubId) {
  router.push({ path: '/clubDetail', query: { id: clubId } })
}

function logout() {
  userStore.clear()
  router.push('/login')
}

/** 是否处于纳新期：未过截止时间 */
function isRecruiting(club) {
  if (!club.recruitDeadline) return true // 空 = 长期纳新
  return new Date(club.recruitDeadline) > new Date()
}

const categoryColors = {
  学术: '',
  体育: 'success',
  文艺: 'warning',
  实践: 'danger',
}
function categoryTag(category) {
  return categoryColors[category] ?? 'info'
}

onMounted(load)
</script>

<style scoped>
.club-list-page {
  min-height: 100vh;
  background: #f0f2f5;
}

/* 顶栏 */
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
  justify-content: space-between;
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

/* 主体 */
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
.search-input {
  width: 320px;
}

/* 卡片网格 */
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
.club-card {
  cursor: pointer;
}
.club-card :deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  height: 140px;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.club-name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}
.club-intro {
  flex: 1;
  margin: 12px 0;
  color: #909399;
  font-size: 13px;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #c0c4cc;
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
