import request from './request'

/**
 * 社联管理员统计接口（StatsController / 附录 A 第 21 项）
 * 权限：UNION_ADMIN 或 SYS_ADMIN
 * overview 返回 { clubStats, activityStats, venueStats }
 */

export function statsOverview() {
  return request.get('/admin/stats')
}

export function clubStats() {
  return request.get('/admin/stats/clubs')
}

export function activityStats() {
  return request.get('/admin/stats/activities')
}

export function venueStats() {
  return request.get('/admin/stats/venues')
}

/**
 * 报表导出：后端返回 CSV 文件流（非 JSON），需以 blob 接收并触发浏览器下载。
 * 注意：不能走 request.js 的统一 JSON 拦截，故单独用 axios 实例。
 */
export async function exportStatsCsv() {
  const token = localStorage.getItem('token')
  const res = await fetch('/api/admin/stats/export', {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) throw new Error(`导出失败 (${res.status})`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `stats-${new Date().toISOString().slice(0, 10).replace(/-/g, '')}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
