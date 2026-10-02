// 全站通用格式化工具

export const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

export function weekdayName(n) {
  const i = Number(n)
  return i >= 1 && i <= 7 ? WEEKDAYS[i - 1] : '—'
}

// "周三 第3-4节 · 1-16周"
export function timeText(c) {
  if (!c) return '—'
  const wd = weekdayName(c.weekday)
  const sec = c.startSection && c.endSection ? `第${c.startSection}-${c.endSection}节` : ''
  const weeks = c.weeks ? `${c.weeks}周` : ''
  return [wd, sec, weeks].filter(Boolean).join(' ')
}

// 课程封面渐变：按 id 取模，保证同一门课颜色稳定
const COVERS = [
  'linear-gradient(135deg, #1e3a5f 0%, #3d6a9e 100%)',
  'linear-gradient(135deg, #2c4f7c 0%, #5b8bc0 100%)',
  'linear-gradient(135deg, #7a4a1e 0%, #e8862e 100%)',
  'linear-gradient(135deg, #1e5f4a 0%, #3d9e7c 100%)',
  'linear-gradient(135deg, #5f1e3a 0%, #a04a6e 100%)',
  'linear-gradient(135deg, #3a3a5f 0%, #6e6ea8 100%)',
  'linear-gradient(135deg, #5f4a1e 0%, #c09a3d 100%)',
  'linear-gradient(135deg, #1e3a5f 0%, #e8862e 130%)'
]

export function coverGradient(id) {
  const i = Math.abs(Number(id) || 0) % COVERS.length
  return COVERS[i]
}

export function coverChar(name) {
  return (name || '课').trim().charAt(0)
}

// 课程状态标签（兼容数字 / 字符串两种后端返回）
export function statusTag(status) {
  const s = String(status ?? '').toUpperCase()
  if (s === '1' || s === 'PUBLISHED' || s === 'ONLINE') return { text: '可选', type: 'success' }
  if (s === '0' || s === 'DRAFT') return { text: '未发布', type: 'info' }
  if (s === '2' || s === 'OFFLINE') return { text: '已下架', type: 'warning' }
  return { text: status === undefined || status === null || status === '' ? '未知' : String(status), type: 'info' }
}

// 选课阶段类型
export const PHASE_TYPES = {
  WISH: '志愿填报',
  MAIN: '正选',
  ADD: '补退选'
}

export function phaseTypeName(t) {
  return PHASE_TYPES[t] || t || '选课'
}

// 订单类型：0普通/1秒杀/2志愿录取/3候补转正
export const ORDER_TYPES = {
  0: { text: '普通选课', type: '' },
  1: { text: '秒杀抢课', type: 'danger' },
  2: { text: '志愿录取', type: 'warning' },
  3: { text: '候补转正', type: 'success' }
}

export function orderTypeTag(t) {
  return ORDER_TYPES[t] || { text: '未知类型', type: 'info' }
}

// 订单状态：1已选上/2已退课
export function orderStatusTag(s) {
  if (Number(s) === 1) return { text: '已选上', type: 'success' }
  if (Number(s) === 2) return { text: '已退课', type: 'info' }
  return { text: '未知', type: 'info' }
}

// 倒计时文案："X 天 X 时 X 分 X 秒"
export function countdownText(ms) {
  const s = Math.max(0, Math.floor(ms / 1000))
  const d = Math.floor(s / 86400)
  const h = Math.floor((s % 86400) / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  const parts = []
  if (d > 0) parts.push(`${d} 天`)
  parts.push(`${h} 时`, `${m} 分`, `${sec} 秒`)
  return parts.join(' ')
}

export function formatDateTime(v) {
  if (!v) return '—'
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const p = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`
}
