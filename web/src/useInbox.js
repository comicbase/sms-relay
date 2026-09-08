import { useCallback, useEffect, useRef, useState } from 'react'
import { cursorFor, fetchDevices, fetchPage, fetchStats } from './inboxData.js'

/** 分页、统计和实时通知分开，翻页不会重复统计全库或下载所有设备。 */
export function useInbox(client) {
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState({ query: '', device: 'all' })
  const [cursors, setCursors] = useState([null])
  const [devices, setDevices] = useState(null)
  const [messages, setMessages] = useState([])
  const [hasNext, setHasNext] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [deviceError, setDeviceError] = useState('')
  const [stats, setStats] = useState(null)
  const [statsError, setStatsError] = useState('')
  const [realtime, setRealtime] = useState('connecting')
  const [lastUpdated, setLastUpdated] = useState(null)
  const [reload, setReload] = useState(0)
  const [manualReload, setManualReload] = useState(0)
  const latest = useRef({})
  const page = cursors.length
  const searching = query.trim() !== filter.query
  latest.current = { page, query: filter.query, searching }

  useEffect(() => {
    if (query.trim() === filter.query) return
    const timer = setTimeout(() => {
      setFilter((old) => old.query === query.trim() ? old : { ...old, query: query.trim() })
      setCursors([null])
    }, 350)
    return () => clearTimeout(timer)
  }, [query, filter.query])

  const setDeviceFilter = (device) => {
    setLoading(true)
    setFilter((old) => ({ ...old, device }))
    setCursors([null])
  }

  useEffect(() => {
    const controller = new AbortController()
    setDeviceError('')
    fetchDevices(client, controller.signal).then((value) => {
      if (!controller.signal.aborted) setDevices((old) => JSON.stringify(old) === JSON.stringify(value) ? old : value)
    }).catch((e) => {
      if (!controller.signal.aborted) setDeviceError(e.message || '设备列表读取失败')
    })
    return () => controller.abort()
  }, [client, manualReload])

  useEffect(() => {
    if (!devices) return
    const controller = new AbortController()
    setLoading(true)
    setError('')
    fetchPage(client, { ...filter, devices, cursor: cursors.at(-1), signal: controller.signal })
      .then((result) => {
        if (controller.signal.aborted) return
        setMessages(result.messages)
        setHasNext(result.hasNext)
        setLastUpdated(new Date())
      }).catch((e) => {
        if (!controller.signal.aborted) setError(e.message || '短信读取失败')
      }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [client, devices, filter, cursors, reload])

  useEffect(() => {
    let controller
    const update = () => {
      if (document.visibilityState === 'hidden') return
      controller?.abort()
      controller = new AbortController()
      const current = controller
      fetchStats(client, current.signal).then((value) => {
        if (!current.signal.aborted) { setStats(value); setStatsError('') }
      }).catch((e) => {
        if (!current.signal.aborted) setStatsError(e.message || '统计读取失败')
      })
    }
    update()
    const timer = setInterval(update, 60000)
    document.addEventListener('visibilitychange', update)
    return () => { clearInterval(timer); controller?.abort(); document.removeEventListener('visibilitychange', update) }
  }, [client, manualReload])

  useEffect(() => {
    let timer
    let disposed = false
    const changed = () => {
      if (disposed) return
      // 一批事件只排一次刷新；历史页/搜索结果不挪动用户当前阅读位置。
      if (timer) return
      timer = setTimeout(() => {
        timer = null
        const state = latest.current
        if (document.visibilityState !== 'hidden' && state.page === 1 && !state.query && !state.searching) {
          setReload((n) => n + 1)
        }
      }, 1500)
    }
    const channel = client.channel('smsrelay-web-live')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'sms_messages' }, changed)
      .subscribe((status) => {
        if (disposed) return
        setRealtime(status === 'SUBSCRIBED' ? 'live' : ['CHANNEL_ERROR', 'TIMED_OUT'].includes(status) ? 'error' : 'connecting')
        // 补齐首次查询到订阅之间的空隙，也补齐断线重连期间的消息。
        if (status === 'SUBSCRIBED') changed()
      })
    const visible = () => { if (document.visibilityState !== 'hidden') changed() }
    document.addEventListener('visibilitychange', visible)
    return () => { disposed = true; clearTimeout(timer); document.removeEventListener('visibilitychange', visible); client.removeChannel(channel) }
  }, [client])

  const refresh = useCallback(() => {
    setLoading(true)
    setCursors([null])
    setManualReload((n) => n + 1)
  }, [])
  const next = () => {
    if (loading || searching || !hasNext || !messages.length || error) return
    setLoading(true)
    setCursors((old) => [...old, cursorFor(messages.at(-1))])
  }
  const previous = () => {
    if (loading || searching || page === 1) return
    setLoading(true)
    setCursors((old) => old.slice(0, -1))
  }
  return { query, setQuery, deviceFilter: filter.device, setDeviceFilter, devices: devices || {}, messages,
    page, hasNext, next, previous, refresh, loading: loading || searching, error: error || deviceError,
    stats, statsError, realtime, lastUpdated }
}
