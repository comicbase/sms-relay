import React, { useCallback, useEffect, useMemo, useState } from 'react'
import {
  Check,
  ChevronRight,
  CircleAlert,
  Clipboard,
  Clock3,
  Database,
  Eye,
  EyeOff,
  Inbox,
  LoaderCircle,
  LogOut,
  MessageSquareText,
  RefreshCw,
  Search,
  Settings,
  ShieldCheck,
  Signal,
  Smartphone,
  X,
} from 'lucide-react'
import { clearConfig, getSavedConfig, makeClient, saveConfig, validateConfig } from './supabase.js'

const TOKYO_TIME = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Tokyo',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
})

const TOKYO_DAY = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: 'long',
  day: 'numeric',
  weekday: 'short',
})

/** 根据“是否配置”和“是否登录”在设置、登录与收件箱三个阶段之间切换。 */
function App() {
  const [config, setConfig] = useState(getSavedConfig)
  const client = useMemo(() => (config.url && config.key ? makeClient(config) : null), [config])
  const [session, setSession] = useState(null)
  const [authReady, setAuthReady] = useState(false)

  useEffect(() => {
    // 首次挂载恢复浏览器会话，并持续监听登录、令牌刷新和退出事件。
    if (!client) {
      setAuthReady(true)
      return undefined
    }
    let active = true
    client.auth.getSession().then(({ data }) => {
      if (active) {
        setSession(data.session)
        setAuthReady(true)
      }
    })
    const { data } = client.auth.onAuthStateChange((_event, nextSession) => setSession(nextSession))
    return () => {
      active = false
      data.subscription.unsubscribe()
    }
  }, [client])

  if (!config.url || !config.key) {
    return <SetupScreen onConfigured={setConfig} />
  }
  if (!authReady) return <FullPageLoading />
  if (!session) return <LoginScreen client={client} onChangeConfig={() => setConfig({ url: '', key: '' })} />
  return <Dashboard client={client} session={session} onChangeConfig={() => setConfig({ url: '', key: '' })} />
}

function Brand() {
  return (
    <div className="brand">
      <span className="brand-mark"><MessageSquareText size={21} strokeWidth={2.2} /></span>
      <span>短信中继台</span>
    </div>
  )
}

/** 首次运行配置页：只接收 Project URL 和 Publishable Key。 */
function SetupScreen({ onConfigured }) {
  const [url, setUrl] = useState('')
  const [key, setKey] = useState('')
  const [error, setError] = useState('')

  const submit = (event) => {
    event.preventDefault()
    const problem = validateConfig(url.trim(), key.trim())
    if (problem) return setError(problem)
    saveConfig(url, key)
    onConfigured(getSavedConfig())
  }

  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Brand />
        <div className="story-copy">
          <span className="eyebrow"><Signal size={14} /> 实时、安全、跨地域</span>
          <h1>手机收到的短信，<br />在日本也能即时查看。</h1>
          <p>连接你现有的 Supabase 项目。短信内容由浏览器直接读取，不经过其他服务器。</p>
        </div>
        <div className="trust-row">
          <span><ShieldCheck size={17} /> RLS 数据隔离</span>
          <span><Clock3 size={17} /> 东京时间显示</span>
        </div>
      </section>
      <section className="auth-panel-wrap">
        <form className="auth-card" onSubmit={submit}>
          <div className="step-badge">首次设置</div>
          <h2>连接 Supabase</h2>
          <p className="muted">填写 Android 应用中使用的同一组公开配置。</p>
          {error && <ErrorBanner>{error}</ErrorBanner>}
          <label>
            <span>Project URL</span>
            <input type="url" value={url} onChange={(e) => setUrl(e.target.value)} placeholder="https://xxxx.supabase.co" autoComplete="url" required />
          </label>
          <label>
            <span>Publishable key</span>
            <textarea value={key} onChange={(e) => setKey(e.target.value)} placeholder="sb_publishable_…" rows="3" required />
          </label>
          <button className="primary-button" type="submit">保存并继续 <ChevronRight size={18} /></button>
          <p className="security-note"><ShieldCheck size={15} /> 配置仅保存在当前浏览器。请勿填写 Secret 或 service_role key。</p>
        </form>
      </section>
    </main>
  )
}

/** 密码直接交给 Supabase Auth；本应用状态和 localStorage 都不保存密码。 */
function LoginScreen({ client, onChangeConfig }) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const login = async (event) => {
    event.preventDefault()
    setLoading(true)
    setError('')
    const { error: authError } = await client.auth.signInWithPassword({ email: email.trim(), password })
    if (authError) setError(authError.message === 'Invalid login credentials' ? '邮箱或密码不正确' : authError.message)
    setLoading(false)
  }

  const reset = async () => {
    await client.auth.signOut()
    clearConfig()
    onChangeConfig()
  }

  return (
    <main className="auth-layout">
      <section className="auth-story">
        <Brand />
        <div className="story-copy">
          <span className="eyebrow"><Signal size={14} /> Supabase 已连接</span>
          <h1>欢迎回来。</h1>
          <p>使用与 Android 应用相同的 Supabase Auth 普通用户登录。</p>
        </div>
        <div className="trust-row"><span><ShieldCheck size={17} /> 密码由 Supabase Auth 验证</span></div>
      </section>
      <section className="auth-panel-wrap">
        <form className="auth-card" onSubmit={login}>
          <div className="step-badge success"><Check size={14} /> 项目已连接</div>
          <h2>登录查看短信</h2>
          <p className="muted">会话将安全保存在当前浏览器。</p>
          {error && <ErrorBanner>{error}</ErrorBanner>}
          <label><span>邮箱</span><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} placeholder="name@example.com" autoComplete="email" required /></label>
          <label>
            <span>密码</span>
            <span className="password-field">
              <input type={showPassword ? 'text' : 'password'} value={password} onChange={(e) => setPassword(e.target.value)} placeholder="输入密码" autoComplete="current-password" required />
              <button type="button" aria-label={showPassword ? '隐藏密码' : '显示密码'} onClick={() => setShowPassword((v) => !v)}>{showPassword ? <EyeOff size={18} /> : <Eye size={18} />}</button>
            </span>
          </label>
          <button className="primary-button" type="submit" disabled={loading}>{loading ? <><LoaderCircle className="spin" size={18} /> 正在登录</> : <>登录 <ChevronRight size={18} /></>}</button>
          <button className="text-button" type="button" onClick={reset}>更换 Supabase 项目</button>
        </form>
      </section>
    </main>
  )
}

/** 登录后的主页面，负责查询、筛选、Realtime 订阅和短信详情。 */
function Dashboard({ client, session, onChangeConfig }) {
  const [messages, setMessages] = useState([])
  const [devices, setDevices] = useState({})
  const [loading, setLoading] = useState(true)
  const [refreshing, setRefreshing] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [deviceFilter, setDeviceFilter] = useState('all')
  const [selected, setSelected] = useState(null)
  const [realtime, setRealtime] = useState('connecting')
  const [lastUpdated, setLastUpdated] = useState(null)

  const loadMessages = useCallback(async (quiet = false) => {
    // 两个查询可以并行执行；RLS 会在数据库端过滤当前用户无权查看的行。
    quiet ? setRefreshing(true) : setLoading(true)
    setError('')
    const [messageResult, deviceResult] = await Promise.all([
      client.from('sms_messages').select('*').order('received_at', { ascending: false }).limit(500),
      client.from('devices').select('id,name'),
    ])
    if (messageResult.error) setError(messageResult.error.message)
    else {
      setMessages(messageResult.data || [])
      setLastUpdated(new Date())
    }
    if (!deviceResult.error) {
      setDevices(Object.fromEntries((deviceResult.data || []).map((device) => [device.id, device.name || '未命名设备'])))
    }
    setLoading(false)
    setRefreshing(false)
  }, [client])

  useEffect(() => {
    // 首次进入 Dashboard 时加载当前快照。
    loadMessages()
  }, [loadMessages])

  useEffect(() => {
    // Realtime 只发送“发生了新增”的信号；收到后复用完整查询，保持排序和设备映射一致。
    const channel = client
      .channel('smsrelay-web-live')
      .on('postgres_changes', { event: 'INSERT', schema: 'public', table: 'sms_messages' }, () => loadMessages(true))
      .subscribe((status) => {
        if (status === 'SUBSCRIBED') setRealtime('live')
        else if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT') setRealtime('error')
        else setRealtime('connecting')
      })
    return () => { client.removeChannel(channel) }
  }, [client, loadMessages])

  // 搜索和设备筛选完全在浏览器内完成，不会为每次键盘输入发起网络请求。
  const filtered = useMemo(() => {
    const normalized = query.trim().toLowerCase()
    return messages.filter((message) => {
      const matchesDevice = deviceFilter === 'all' || message.device_id === deviceFilter
      const haystack = `${message.sender || ''} ${message.body || ''} ${devices[message.device_id] || ''}`.toLowerCase()
      return matchesDevice && (!normalized || haystack.includes(normalized))
    })
  }, [messages, query, deviceFilter, devices])

  const grouped = useMemo(() => {
    return filtered.reduce((result, message) => {
      const date = new Date(message.received_at || message.created_at)
      const key = TOKYO_DAY.format(date)
      if (!result[key]) result[key] = []
      result[key].push(message)
      return result
    }, {})
  }, [filtered])

  const logout = async () => client.auth.signOut()
  const reset = async () => {
    await client.auth.signOut()
    clearConfig()
    onChangeConfig()
  }

  const todayCount = messages.filter((message) => isTodayInTokyo(message.received_at || message.created_at)).length

  return (
    <div className="app-shell">
      <header className="topbar">
        <Brand />
        <div className="top-actions">
          <span className={`live-pill ${realtime}`}><span />{realtime === 'live' ? '实时连接' : realtime === 'error' ? '实时连接异常' : '正在连接'}</span>
          <button className="icon-button" onClick={() => loadMessages(true)} disabled={refreshing} aria-label="刷新"><RefreshCw className={refreshing ? 'spin' : ''} size={18} /></button>
          <div className="account-chip"><span>{session.user.email?.slice(0, 1).toUpperCase()}</span><div><small>当前账号</small><strong>{session.user.email}</strong></div></div>
          <button className="icon-button" onClick={logout} aria-label="退出登录"><LogOut size={18} /></button>
        </div>
      </header>

      <main className="dashboard">
        <section className="dashboard-head">
          <div><p className="kicker">MESSAGE CENTER</p><h1>短信收件箱</h1><p>所有时间均以日本标准时间（JST）显示</p></div>
          <div className="summary-row">
            <div className="summary-card"><span className="summary-icon amber"><Inbox size={20} /></span><div><strong>{messages.length}</strong><small>全部短信</small></div></div>
            <div className="summary-card"><span className="summary-icon green"><Signal size={20} /></span><div><strong>{todayCount}</strong><small>今日收到</small></div></div>
            <div className="summary-card"><span className="summary-icon blue"><Smartphone size={20} /></span><div><strong>{Object.keys(devices).length}</strong><small>已连接设备</small></div></div>
          </div>
        </section>

        <section className="inbox-card">
          <div className="inbox-toolbar">
            <div className="search-box"><Search size={18} /><input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="搜索号码、内容或设备…" aria-label="搜索短信" />{query && <button onClick={() => setQuery('')} aria-label="清空搜索"><X size={16} /></button>}</div>
            <select value={deviceFilter} onChange={(e) => setDeviceFilter(e.target.value)} aria-label="按设备筛选">
              <option value="all">全部设备</option>
              {Object.entries(devices).map(([id, name]) => <option value={id} key={id}>{name}</option>)}
            </select>
            <span className="updated-at">{lastUpdated ? `更新于 ${TOKYO_TIME.format(lastUpdated)}` : '正在读取'}</span>
          </div>

          {error && <div className="load-error"><CircleAlert size={18} /><span><strong>无法读取短信</strong>{friendlyDataError(error)}</span><button onClick={() => loadMessages()}>重试</button></div>}
          {loading ? <MessageSkeleton /> : filtered.length === 0 ? <EmptyState hasQuery={Boolean(query || deviceFilter !== 'all')} /> : (
            <div className="message-groups">
              {Object.entries(grouped).map(([day, dayMessages]) => (
                <section className="message-group" key={day}>
                  <div className="date-divider"><span>{day}</span><i /></div>
                  <div className="message-list">
                    {dayMessages.map((message) => (
                      <button className="message-row" key={message.id || `${message.device_id}-${message.client_message_id}`} onClick={() => setSelected(message)}>
                        <span className="sender-avatar">{senderInitial(message.sender)}</span>
                        <span className="message-main"><span className="message-title"><strong>{message.sender || '未知号码'}</strong><em>{devices[message.device_id] || 'Android 手机'}</em></span><span className="message-preview">{message.body || '（空短信）'}</span></span>
                        <span className="message-meta"><time>{TOKYO_TIME.format(new Date(message.received_at || message.created_at))}</time>{message.sim_slot != null && <small>SIM {Number(message.sim_slot) + 1}</small>}</span>
                        <ChevronRight className="row-arrow" size={18} />
                      </button>
                    ))}
                  </div>
                </section>
              ))}
            </div>
          )}
        </section>
        <footer><button onClick={reset}><Settings size={14} /> 更换 Supabase 项目</button><span><Database size={14} /> 数据直接来自你的 Supabase 项目</span></footer>
      </main>
      {selected && <MessageDrawer message={selected} deviceName={devices[selected.device_id]} onClose={() => setSelected(null)} />}
    </div>
  )
}

function MessageDrawer({ message, deviceName, onClose }) {
  const [copied, setCopied] = useState(false)
  const copy = async () => {
    await navigator.clipboard.writeText(message.body || '')
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }
  return (
    <div className="drawer-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <aside className="drawer" role="dialog" aria-modal="true" aria-label="短信详情">
        <div className="drawer-head"><div><span className="eyebrow">短信详情</span><h2>{message.sender || '未知号码'}</h2></div><button className="icon-button" onClick={onClose} aria-label="关闭"><X size={20} /></button></div>
        <div className="detail-meta">
          <div><Clock3 size={17} /><span><small>接收时间（JST）</small><strong>{formatFullTime(message.received_at || message.created_at)}</strong></span></div>
          <div><Smartphone size={17} /><span><small>来源设备</small><strong>{deviceName || 'Android 手机'}{message.sim_slot != null ? ` · SIM ${Number(message.sim_slot) + 1}` : ''}</strong></span></div>
        </div>
        <div className="message-body-card"><p>{message.body || '（空短信）'}</p><button onClick={copy}>{copied ? <Check size={16} /> : <Clipboard size={16} />}{copied ? '已复制' : '复制内容'}</button></div>
        <p className="drawer-note"><ShieldCheck size={15} /> 内容通过 Supabase RLS 权限读取，仅登录用户可见。</p>
      </aside>
    </div>
  )
}

function ErrorBanner({ children }) { return <div className="error-banner"><CircleAlert size={17} /><span>{children}</span></div> }
function FullPageLoading() { return <main className="full-loading"><span className="brand-mark"><MessageSquareText size={23} /></span><LoaderCircle className="spin" size={24} /><p>正在恢复安全会话…</p></main> }
function MessageSkeleton() { return <div className="skeleton-list">{[1,2,3,4].map((i) => <div className="skeleton-row" key={i}><i /><span><b /><b /></span><em /></div>)}</div> }
function EmptyState({ hasQuery }) { return <div className="empty-state"><span><Inbox size={28} /></span><h3>{hasQuery ? '没有匹配的短信' : '尚未收到短信'}</h3><p>{hasQuery ? '尝试更换关键词或筛选设备。' : 'Android 手机收到并上传短信后，会实时出现在这里。'}</p></div> }

function senderInitial(sender) {
  if (!sender) return '?'
  const clean = sender.replace(/^\+/, '')
  return clean.slice(-2)
}

function isTodayInTokyo(value) {
  if (!value) return false
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date())
  const day = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Tokyo' }).format(new Date(value))
  return today === day
}

function formatFullTime(value) {
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: 'long', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(new Date(value))
}

function friendlyDataError(error) {
  if (/permission|policy|row-level security/i.test(error)) return '当前账号没有读取权限，请检查 sms_messages 的 SELECT RLS 策略。'
  if (/relation.*does not exist/i.test(error)) return '找不到数据表，请确认当前连接的是 Android 应用使用的项目。'
  return error
}

export default App
