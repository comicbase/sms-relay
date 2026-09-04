import test from 'node:test'
import assert from 'node:assert/strict'
import { createClient } from '@supabase/supabase-js'
import { cursorFor, fetchPage, fetchStats, fetchDevices, PAGE_SIZE, quoteFilter, searchExpression, tokyoDayBounds } from '../src/inboxData.js'

function mockClient(reply) {
  const requests = []
  const client = createClient('https://example.invalid', 'test-key', {
    auth: { persistSession: false, autoRefreshToken: false, detectSessionInUrl: false },
    global: { fetch: async (input, options) => {
      const request = { url: new URL(input), ...options }
      requests.push(request)
      return reply(request)
    } },
  })
  return { client, requests }
}
const json = (rows) => new Response(JSON.stringify(rows), { status: 200, headers: { 'Content-Type': 'application/json' } })

test('first page requests only 21 rows and exposes 20 with stable tie ordering', async () => {
  const { client, requests } = mockClient(() => json(Array.from({ length: 21 }, (_, id) => ({ id }))))
  const result = await fetchPage(client)
  assert.equal(result.messages.length, 20)
  assert.equal(result.hasNext, true)
  const params = requests[0].url.searchParams
  assert.equal(params.get('limit'), String(PAGE_SIZE + 1))
  assert.equal(params.get('order'), 'received_at.desc,id.desc')
  assert.equal(params.has('offset'), false)
  assert.equal(params.get('select').includes('*'), false)
  assert.equal(new Headers(requests[0].headers).has('prefer'), false)
})

test('search + device + cursor are combined as an intersection, without offset', async () => {
  const { client, requests } = mockClient(() => json([]))
  const time = '2026-09-03T12:00:00.123456+00:00'
  const cursor = cursorFor({ id: '01234567-0000-0000-0000-000000000001', received_at: time })
  await fetchPage(client, { query: 'hello', device: 'device-a', cursor })
  const params = requests[0].url.searchParams
  assert.equal(params.get('device_id'), 'eq.device-a')
  assert.equal(params.getAll('or').length, 1)
  assert.equal(params.get('or'), `(and(or(sender.ilike."%hello%",recipient.ilike."%hello%",body.ilike."%hello%"),or(received_at.lt."${time}",and(received_at.eq."${time}",id.lt."${cursor.id}"))))`)
  assert.equal(params.get('received_at'), `lte.${time}`)
})

test('reserved characters are quoted and cannot add filter operators', () => {
  assert.equal(quoteFilter('a"\\b'), '"a\\"\\\\b"')
  const expr = searchExpression('a,b).id.gt.0%_', {})
  assert.ok(expr.includes('sender.ilike."%a,b).id.gt.0\\\\%\\\\_%"'))
  assert.equal(searchExpression('   '), '')
})

test('matching device names expands server-side search to their device IDs', () => {
  assert.ok(searchExpression('phone', { 'device-a': 'Test PHONE', 'device-b': 'Tablet' }).endsWith('device_id.in.("device-a")'))
})

test('last page has no next even when it contains exactly 20 rows', async () => {
  const { client } = mockClient(() => json(Array.from({ length: 20 }, (_, id) => ({ id }))))
  assert.equal((await fetchPage(client)).hasNext, false)
})

test('counts use HEAD on all rows, with JST half-open day boundaries', async () => {
  const { client, requests } = mockClient(({ url }) => new Response(null, { status: 200, headers: { 'Content-Range': `*/${url.searchParams.has('received_at') ? 123 : 9999}` } }))
  const stats = await fetchStats(client, new AbortController().signal, new Date('2026-09-03T15:00:00Z'))
  assert.equal(stats.total, 9999)
  assert.equal(stats.today, 123)
  assert.ok(requests.every((r) => r.method === 'HEAD' && !r.url.searchParams.has('limit')))
  assert.deepEqual(requests[1].url.searchParams.getAll('received_at'), ['gte.2026-09-03T15:00:00.000Z', 'lt.2026-09-04T15:00:00.000Z'])
  assert.deepEqual(tokyoDayBounds(new Date('2026-09-03T14:59:59Z')), { start: '2026-09-02T15:00:00.000Z', end: '2026-09-03T15:00:00.000Z' })
})

test('errors propagate instead of appearing as empty lists or zero counts', async () => {
  const { client } = mockClient(() => new Response(JSON.stringify({ message: 'denied', code: '42501' }), { status: 403 }))
  await assert.rejects(fetchPage(client), { message: 'denied' })
  await assert.rejects(fetchStats(client, new AbortController().signal), { message: 'denied' })
})

test('devices are fetched in bounded batches beyond the default API limit', async () => {
  const { client, requests } = mockClient(({ url }) => json(url.searchParams.has('id') ? [{ id: '501', name: 'Last' }] : Array.from({ length: 500 }, (_, i) => ({ id: String(i + 1), name: 'Test' }))))
  const devices = await fetchDevices(client, new AbortController().signal)
  assert.equal(Object.keys(devices).length, 501)
  assert.equal(requests[1].url.searchParams.get('id'), 'gt.500')
})
