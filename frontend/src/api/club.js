import request from './request'

/**
 * 社团接口（对应后端 ClubController / 附录 A）
 * 列表: GET /api/clubs?status&keyword&page&size（匿名可访问，默认只返回已成立）
 * 详情: GET /api/clubs/{id}
 * 创建: POST /api/clubs（LEADER 角色）
 * 申请入社: POST /api/clubs/{id}/applications { applyReason }
 */

export function listClubs(params) {
  // 返回 { total, page, size, rows: [...] }
  return request.get('/clubs', { params })
}

export function getClub(id) {
  return request.get(`/clubs/${id}`)
}

export function createClub(data) {
  return request.post('/clubs', data)
}

export function applyJoinClub(clubId, applyReason) {
  return request.post(`/clubs/${clubId}/applications`, { applyReason })
}
