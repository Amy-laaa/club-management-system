import request from './request'

/**
 * 成员资格接口（对应后端 MembershipController / 附录 A）
 * 我的社团与申请: GET /api/memberships/mine（返回 List<Membership>）
 * 社团待审核申请: GET /api/club/memberships?clubId=&page=&size=（社长）
 * 审批入社: POST /api/club/memberships/{id}/audit
 * 正式成员名单: GET /api/clubs/{clubId}/members?page=&size=（社长，成员管理页）
 */

export function listMyMemberships() {
  // 元素字段: membershipId, clubId, memberRole, applyReason, status, joinedAt, createdAt
  return request.get('/memberships/mine')
}

export function listPendingMemberships(clubId, params) {
  return request.get('/club/memberships', { params: { clubId, ...params } })
}

export function auditMembership(id, { result, remark }) {
  return request.post(`/club/memberships/${id}/audit`, { result, remark })
}

/** 正式成员名单 → { total, page, size, rows: [{ membershipId, studentNo, realName, clubName, memberRole, joinedAt, status }] } */
export function listClubMembers(clubId, params) {
  return request.get(`/clubs/${clubId}/members`, { params })
}
