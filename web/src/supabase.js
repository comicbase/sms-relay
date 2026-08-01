import { createClient } from '@supabase/supabase-js'

const URL_KEY = 'smsrelay.supabase.url'
const PUBLIC_KEY_KEY = 'smsrelay.supabase.publishableKey'

// 构建时环境变量优先；没有预置时再允许用户在首次打开页面时配置。
const buildConfig = {
  url: import.meta.env.VITE_SUPABASE_URL?.trim() || '',
  key: import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY?.trim() || '',
}

export function getSavedConfig() {
  if (buildConfig.url && buildConfig.key) return buildConfig
  return {
    url: localStorage.getItem(URL_KEY) || '',
    key: localStorage.getItem(PUBLIC_KEY_KEY) || '',
  }
}

export function saveConfig(url, key) {
  // URL 与 Publishable Key 都是公开的项目配置；用户密码从不经过此存储。
  localStorage.setItem(URL_KEY, url.trim().replace(/\/$/, ''))
  localStorage.setItem(PUBLIC_KEY_KEY, key.trim())
}

export function clearConfig() {
  localStorage.removeItem(URL_KEY)
  localStorage.removeItem(PUBLIC_KEY_KEY)
}

export function validateConfig(url, key) {
  let parsed
  try {
    parsed = new URL(url)
  } catch {
    return 'Project URL 格式不正确'
  }
  if (parsed.protocol !== 'https:' || !parsed.hostname.endsWith('.supabase.co')) {
    return '请输入 https://…supabase.co 格式的 Project URL'
  }
  if (!key.trim()) return '请输入 Publishable key'
  // 在客户端主动拒绝高权限密钥，降低初学者误配置造成数据泄露的风险。
  if (/service[_-]?role|sb_secret_/i.test(key)) {
    return '不能在网页中使用 Secret 或 service_role key'
  }
  return ''
}

export function makeClient(config) {
  // Supabase SDK 负责持久化 Session 和在 Access Token 到期前自动刷新。
  return createClient(config.url, config.key, {
    auth: {
      persistSession: true,
      autoRefreshToken: true,
      detectSessionInUrl: true,
      storageKey: `smsrelay-auth-${new URL(config.url).hostname}`,
    },
  })
}
