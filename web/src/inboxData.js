export const PAGE_SIZE = 20
const FIELDS = 'id,device_id,client_message_id,sender,recipient,body,received_at,created_at,sim_slot'

// PostgREST 的逻辑过滤器不是 SQL 字符串。引号和反斜线必须先按其语法转义。
export function quoteFilter(value) {
  return `"${String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"')}"`
}

export function searchExpression(query, devices = {}) {
  const text = query.trim()
  if (!text) return ''
  // % 和 _ 按字面搜索；* 保留 PostgREST 的通配符语义（% 的别名）。
  const pattern = quoteFilter(`%${text.replace(/[\\%_]/g, '\\$&')}%`)
  const terms = ['sender', 'recipient', 'body'].map((field) => `${field}.ilike.${pattern}`)
  const ids = Object.entries(devices).filter(([, name]) => name.toLowerCase().includes(text.toLowerCase())).map(([id]) => quoteFilter(id))
  if (ids.length) terms.push(`device_id.in.(${ids.join(',')})`)
  return terms.join(',')
}

export function cursorFor(message) {
  return { time: message.received_at, id: message.id }
}

export async function fetchPage(client, { query = '', device = 'all', devices = {}, cursor = null, signal } = {}) {
  let request = client.from('sms_messages').select(FIELDS)
    .order('received_at', { ascending: false }).order('id', { ascending: false }).limit(PAGE_SIZE + 1)
  if (device !== 'all') request = request.eq('device_id', device)
  const expressions = []
  const search = searchExpression(query, devices)
  if (search) expressions.push(`or(${search})`)
  if (cursor) {
    const time = quoteFilter(cursor.time)
    expressions.push(`or(received_at.lt.${time},and(received_at.eq.${time},id.lt.${quoteFilter(cursor.id)}))`)
    request = request.lte('received_at', cursor.time)
  }
  // 只生成一个逻辑参数，确保搜索条件和翻页条件取交集。
  if (expressions.length) request = request.or(`and(${expressions.join(',')})`)
  if (signal) request = request.abortSignal(signal)
  const { data, error } = await request
  if (error) throw error
  const rows = data || []
  return { messages: rows.slice(0, PAGE_SIZE), hasNext: rows.length > PAGE_SIZE }
}

export function tokyoDayBounds(now = new Date()) {
  const day = new Date(now.getTime() + 9 * 3600000).toISOString().slice(0, 10)
  const start = new Date(`${day}T00:00:00+09:00`)
  return { start: start.toISOString(), end: new Date(start.getTime() + 86400000).toISOString() }
}

export async function fetchStats(client, signal, now = new Date()) {
  const { start, end } = tokyoDayBounds(now)
  // HEAD + count 只返回全库计数，不下载短信正文；RLS 仍限制为当前用户。
  const count = () => client.from('sms_messages').select('id', { count: 'exact', head: true })
  const [total, today] = await Promise.all([
    count().abortSignal(signal),
    count().gte('received_at', start).lt('received_at', end).abortSignal(signal),
  ])
  if (total.error || today.error) throw total.error || today.error
  if (total.count == null || today.count == null) throw new Error('服务端未返回短信计数')
  return { total: total.count, today: today.count, updatedAt: now }
}

export async function fetchDevices(client, signal) {
  const devices = {}
  let after = null
  // 设备名只有少量元数据；分批读取，避免 API 默认行数上限造成静默遗漏。
  for (;;) {
    let request = client.from('devices').select('id,name').order('id').limit(500).abortSignal(signal)
    if (after) request = request.gt('id', after)
    const { data, error } = await request
    if (error) throw error
    for (const row of data || []) devices[row.id] = row.name || '未命名设备'
    if (!data || data.length < 500) return devices
    after = data.at(-1).id
  }
}
