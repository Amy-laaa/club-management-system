<template>
  <div class="notice">
    <!-- 顶栏 -->
    <header class="topbar">
      <h1 class="logo">校园社团管理</h1>
      <nav class="nav">
        <router-link to="/clubList"><el-button text>社团</el-button></router-link>
        <router-link to="/activityList"><el-button text>活动</el-button></router-link>
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
        <h2>公告中心</h2>
        <p class="tip">校级公告与社团公告（文档表 2-9 的 notice 页面）</p>
      </div>

      <el-alert type="warning" show-icon :closable="false" style="margin-bottom: 16px">
        <template #title>后端公告接口尚未实现，本页暂为占位状态</template>
        <div class="alert-body">
          数据库 <code>t_notice</code> 表与演示数据已就绪（含 1 条校级置顶公告、1 条社团公告），
          但后端缺少 <code>NoticeController / NoticeService</code>，前端无接口可对接。
          接口就绪后本页可直接切换为真实列表，无需改动路由与页面结构。
        </div>
      </el-alert>

      <!-- 建议契约（供组内确认，接口就绪后据此对接） -->
      <el-card shadow="never" class="contract-card">
        <template #header>建议接口契约（待后端实现，供组内评审）</template>
        <el-table :data="contracts" border>
          <el-table-column prop="method" label="方法" width="90" align="center" />
          <el-table-column prop="path" label="路径" min-width="240" />
          <el-table-column prop="desc" label="说明" min-width="240" />
          <el-table-column prop="auth" label="权限" width="160" />
        </el-table>
        <div class="contract-note">
          依据 <code>t_notice</code> 字段（club_id 可空=校级、scope 0本社团/1全校、is_pinned 置顶、status 0已撤回/1已发布）
          与文档表 2-18「校级置顶最多 3 条」规则拟定。
        </div>
      </el-card>

      <el-card shadow="never" style="margin-top: 16px">
        <template #header>已入库的公告数据（来自 demo-data，可作接口调试样本）</template>
        <el-table :data="demoRows" border>
          <el-table-column prop="scope" label="范围" width="110" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="content" label="正文" min-width="260" show-overflow-tooltip />
          <el-table-column prop="pinned" label="置顶" width="80" align="center" />
        </el-table>
      </el-card>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '../stores/user'

const router = useRouter()
const userStore = useUserStore()
const isAdmin = computed(() => ['UNION_ADMIN', 'SYS_ADMIN'].includes(userStore.role))

const contracts = ref([
  { method: 'GET', path: '/api/notices?scope=&clubId=&page=&size=', desc: '公告列表（校级/社团，按置顶+时间排序）', auth: '登录用户' },
  { method: 'GET', path: '/api/notices/{id}', desc: '公告详情', auth: '登录用户' },
  { method: 'POST', path: '/api/notices', desc: '发布公告（校级限社联，社团限本社团负责人）', auth: 'LEADER / UNION_ADMIN' },
  { method: 'POST', path: '/api/notices/{id}/pin', desc: '置顶/取消置顶（校级最多 3 条）', auth: 'LEADER / UNION_ADMIN' },
  { method: 'POST', path: '/api/notices/{id}/withdraw', desc: '撤回公告（status → 0）', auth: '发布者 / UNION_ADMIN' }
])

// demo-data 中的公告样本（静态展示，接口就绪后替换为接口数据）
const demoRows = ref([
  { scope: '校级', title: '社团纳新季启动', content: '全校社团纳新于 10 月 10 日开始。', pinned: '是' },
  { scope: '计算机协会', title: '计算机协会招新', content: '欢迎加入计算机协会！', pinned: '否' }
])

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
.page-head h2 { margin: 0 0 4px; }
.tip { color: #909399; font-size: 13px; margin: 0 0 16px; }
.alert-body {
  font-size: 13px;
  line-height: 1.7;
  margin-top: 4px;
}
code {
  background: #f4f4f5;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
}
.contract-note {
  margin-top: 10px;
  color: #909399;
  font-size: 12px;
  line-height: 1.7;
}
</style>
