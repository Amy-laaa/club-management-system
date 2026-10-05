/**
 * 隐私脱敏工具（安全需求 S-04：学号、手机号脱敏展示，日志不得打印明文密码）
 *
 * 原则：保留首尾若干字符便于核对，中间以 * 占位，长度与原值一致。
 * 纪律：凡是"展示他人信息"的场景（入社审批、成员名单、签到名单、场地申请人）
 *       一律经过这里，禁止在模板里直接渲染 studentNo / mobile 原文。
 */

/** 学号：20260006 → 2026**06（保留前 4 位、后 2 位） */
export function maskStudentNo(studentNo) {
  if (!studentNo) return ''
  const s = String(studentNo)
  if (s.length <= 2) return '*'.repeat(s.length)
  if (s.length <= 6) return s[0] + '*'.repeat(s.length - 2) + s[s.length - 1]
  return s.slice(0, 4) + '*'.repeat(s.length - 6) + s.slice(-2)
}

/** 手机号：13800000005 → 138****0005（保留前 3 位、后 4 位） */
export function maskMobile(mobile) {
  if (!mobile) return ''
  const s = String(mobile)
  if (s.length <= 7) return '*'.repeat(s.length)
  return s.slice(0, 3) + '*'.repeat(s.length - 7) + s.slice(-4)
}

/** 姓名：张三 → 张*，李同学 → 李**（备用：对外公示等非成员可见场景） */
export function maskName(name) {
  if (!name) return ''
  const s = String(name)
  if (s.length <= 1) return s
  return s[0] + '*'.repeat(s.length - 1)
}
