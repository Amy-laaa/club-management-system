import request from './request'

/** 场地列表 + 指定日期时段占用（需登录）。date 格式 YYYY-MM-DD */
export function listVenues(date) {
  return request.get('/api/venues', { params: { date } })
}

/** 社团负责人申请场地 */
export function applyVenue(data) {
  return request.post('/api/venues/applications', data)
}

/** 我的场地申请列表 */
export function myVenueApplications() {
  return request.get('/api/venues/applications/mine')
}
