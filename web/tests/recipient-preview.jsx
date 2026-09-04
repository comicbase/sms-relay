// 隔离 UI 测试：525 条虚构短信，不连接 Supabase，不读取真实配置。
import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import { Dashboard } from '../src/App.jsx'
import '../src/styles.css'

let rows = Array.from({ length: 525 }, (_, index) => ({
  id: `00000000-0000-0000-0000-${String(index + 1).padStart(12, '0')}`,
  client_message_id: `fake-${index + 1}`, sender: '10001',
  recipient: index === 2 ? null : index % 2 ? '09000000000' : '+8613800000000',
  sim_slot: index % 2, device_id: index % 2 ? 'device-b' : 'device-a',
  body: index === 0 ? '最旧测试 slow' : index === 1 ? 'fast' : `虚构分页测试 ${index + 1}`,
  received_at: '2026-09-03T10:00:00.000000+00:00',
}))
const devices = [{ id: 'device-a', name: '虚构手机 A' }, { id: 'device-b', name: '虚构手机 B' }]
let listener = () => {}
let pageRequests = 0
let countRequests = 0
let failNext = false
let notify = () => {}
const quoted = '"((?:\\\\.|[^"\\\\])*)"'
const extract = (value, prefix) => {
  const match = value.match(new RegExp(prefix + quoted))
  return match ? JSON.parse(`"${match[1]}"`) : null
}
class Query {
  constructor(table) { this.table = table; this.filters = []; this.orders = []; this.max = Infinity; this.expression = '' }
  select(fields, options = {}) { this.head = options.head; return this }
  order(field, { ascending = true } = {}) { this.orders.push([field, ascending]); return this }
  limit(n) { this.max = n; return this }
  abortSignal() { return this } // 故意模拟无法取消的慢响应，检查 UI 的过期响应保护。
  eq(k, v) { this.filters.push((r) => r[k] === v); return this }
  gt(k, v) { this.filters.push((r) => r[k] > v); return this }
  gte(k, v) { this.filters.push((r) => r[k] >= v); return this }
  lt(k, v) { this.filters.push((r) => r[k] < v); return this }
  lte(k, v) { this.filters.push((r) => r[k] <= v); return this }
  or(expression) { this.expression = expression; return this }
  async execute() {
    const isPage = this.table === 'sms_messages' && !this.head
    if (isPage) pageRequests++
    if (this.head) countRequests++
    notify()
    if (isPage && failNext) { failNext = false; return { error: { message: '虚构网络错误，请重试' } } }
    let data = [...(this.table === 'devices' ? devices : rows)]
    const pattern = extract(this.expression, 'sender\\.ilike\\.')
    const search = pattern?.slice(1, -1).replace(/\\([\\%_])/g, '$1').toLowerCase()
    if (search) data = data.filter((r) => [r.sender, r.recipient, r.body, devices.find((d) => d.id === r.device_id)?.name].some((v) => v?.toLowerCase().includes(search)))
    const time = extract(this.expression, 'received_at\\.lt\\.')
    const id = extract(this.expression, 'id\\.lt\\.')
    if (time) data = data.filter((r) => r.received_at < time || (r.received_at === time && r.id < id))
    for (const predicate of this.filters) data = data.filter(predicate)
    for (const [field, asc] of [...this.orders].reverse()) data.sort((a, b) => (a[field] < b[field] ? -1 : a[field] > b[field] ? 1 : 0) * (asc ? 1 : -1))
    const response = { data: this.head ? null : data.slice(0, this.max), count: this.head ? data.length : null }
    await new Promise((resolve) => setTimeout(resolve, search === 'slow' ? 1200 : 50))
    return response
  }
  then(resolve, reject) { return this.execute().then(resolve, reject) }
}
const channel = { on(_type, _filter, callback) { listener = callback; return this }, subscribe(callback) { callback('SUBSCRIBED'); return this } }
const client = { from: (table) => new Query(table), channel: () => channel, removeChannel() {}, auth: { signOut: async () => {} } }
function Preview() {
  const [, redraw] = useState(0)
  notify = () => queueMicrotask(() => redraw((n) => n + 1))
  const insert = () => {
    const id = rows.length + 1
    rows = [{ ...rows[0], id: `00000000-0000-0000-0000-${String(id).padStart(12, '0')}`, body: `新短信测试 ${id}` }, ...rows]
    for (let i = 0; i < 10; i++) listener()
  }
  return <><div style={{ padding: 12 }}><strong>虚构数据测试</strong> <button onClick={insert}>模拟一批新短信事件</button> <button onClick={() => { failNext = true }}>下次列表请求失败</button> <output>列表请求 {pageRequests} · 统计请求 {countRequests}</output></div><Dashboard client={client} session={{ user: { email: 'test@example.invalid' } }} onChangeConfig={() => {}} /></>
}
createRoot(document.getElementById('root')).render(<Preview />)
