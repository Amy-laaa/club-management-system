import { createRouter, createWebHistory } from 'vue-router'

/**
 * 路由表：页面清单对齐详细设计说明书表 2-9（16 个页面）
 * 路径命名：小驼峰，与文档保持零歧义
 */
const routes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { title: '登录', anonymous: true },
  },
  {
    path: '/register',
    name: 'register',
    component: () => import('../views/RegisterView.vue'),
    meta: { title: '注册', anonymous: true },
  },
  {
    path: '/',
    redirect: '/clubList',
  },
  {
    path: '/clubList',
    name: 'clubList',
    component: () => import('../views/ClubListView.vue'),
    meta: { title: '社团列表', anonymous: true },
  },
  {
    path: '/clubDetail',
    name: 'clubDetail',
    component: () => import('../views/ClubDetailView.vue'),
    meta: { title: '社团详情' },
  },
  {
    path: '/activityList',
    name: 'activityList',
    component: () => import('../views/ActivityListView.vue'),
    meta: { title: '活动列表', anonymous: true },
  },
  {
    path: '/activityDetail',
    name: 'activityDetail',
    component: () => import('../views/ActivityDetailView.vue'),
    meta: { title: '活动详情' },
  },
  {
    path: '/myRegistrations',
    name: 'myRegistrations',
    component: () => import('../views/MyRegistrationsView.vue'),
    meta: { title: '我的报名' },
  },
  {
    path: '/clubCreate',
    name: 'clubCreate',
    component: () => import('../views/ClubCreateView.vue'),
    meta: { title: '创建社团' },
  },
  {
    path: '/activityCreate',
    name: 'activityCreate',
    component: () => import('../views/ActivityCreateView.vue'),
    meta: { title: '发布活动' },
  },
  {
    path: '/venueApply',
    name: 'venueApply',
    component: () => import('../views/VenueApplyView.vue'),
    meta: { title: '场地申请' },
  },
  {
    path: '/applyList',
    name: 'applyList',
    component: () => import('../views/ApplyListView.vue'),
    meta: { title: '审批列表' },
  },
  {
    path: '/checkIn',
    name: 'checkIn',
    component: () => import('../views/CheckInView.vue'),
    meta: { title: '活动签到' },
  },
  {
    path: '/notice',
    name: 'notice',
    component: () => import('../views/NoticeView.vue'),
    meta: { title: '公告管理' },
  },
  {
    path: '/stats',
    name: 'stats',
    component: () => import('../views/StatsView.vue'),
    meta: { title: '统计报表' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 全局守卫：非匿名页面必须登录
router.beforeEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} - 社团管理系统` : '社团管理系统'
  if (!to.meta.anonymous && !localStorage.getItem('token')) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
