import request from './request'

/**
 * 公告接口（对应后端 NoticeController，契约 #22 #23）
 * 列表: GET /api/notices?scope=&clubId=&page=&size=（默认全校 scope=1）
 * 详情: GET /api/notices/{id}
 * 发布: POST /api/notices { title, content, isPinned }（社长→本社团；社联/系统管理员→全校）
 * 置顶: POST /api/notices/{id}/pin { isPinned }（不传则切换；校级最多 3 条）
 * 撤回: POST /api/notices/{id}/withdraw（发布者本人或社联管理员）
 */

export function listNotices(params) {
  // 返回 { total, rows/list, page, size }
  return request.get('/notices', { params })
}

export function getNotice(id) {
  return request.get(`/notices/${id}`)
}

export function publishNotice(data) {
  // data: { title, content, isPinned } → { noticeId }
  return request.post('/notices', data)
}

export function pinNotice(id, isPinned) {
  // isPinned: 1 置顶 / 0 取消 / 不传则按当前状态切换
  return request.post(`/notices/${id}/pin`, isPinned === null || isPinned === undefined ? {} : { isPinned })
}

export function withdrawNotice(id) {
  return request.post(`/notices/${id}/withdraw`)
}
