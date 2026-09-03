// 仅供 Vite 开发服务器下的 UI 验证。无真实账号、不联网、不写入 Supabase。
import React from 'react'
import { createRoot } from 'react-dom/client'
import { Dashboard } from '../src/App.jsx'
import '../src/styles.css'

const messages = [
  { id: 1, sender: '10001', recipient: '+8613800000000', sim_slot: 0, body: 'SIM 1 测试短信', device_id: 'test-device', received_at: '2026-09-03T01:00:00Z' },
  { id: 2, sender: '10002', recipient: '09000000000', sim_slot: 1, body: 'SIM 2 测试短信', device_id: 'test-device', received_at: '2026-09-03T00:00:00Z' },
  { id: 3, sender: '10003', body: '旧版短信：没有接收号码字段', device_id: 'test-device', received_at: '2026-09-02T23:00:00Z' },
  { id: 4, sender: '10004', recipient: null, body: '卡槽未知或未配置号码', device_id: 'test-device', received_at: '2026-09-02T22:00:00Z' },
]
const channel = { on() { return this }, subscribe(callback) { callback('SUBSCRIBED'); return this } }
const client = {
  from(table) {
    return { select() {
      if (table === 'devices') return Promise.resolve({ data: [{ id: 'test-device', name: '虚构测试手机' }] })
      return { order() { return { limit() { return Promise.resolve({ data: messages }) } } } }
    } }
  },
  channel: () => channel,
  removeChannel() {},
  auth: { signOut: async () => {} },
}
createRoot(document.getElementById('root')).render(
  <Dashboard client={client} session={{ user: { email: 'test@example.invalid' } }} onChangeConfig={() => {}} />,
)
