package com.example.smsrelay.data.remote

import com.example.smsrelay.data.local.SmsEntity
import com.example.smsrelay.data.session.SessionStore
import com.example.smsrelay.data.session.SupabaseSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

/**
 * 使用 JDK [HttpURLConnection] 演示 Supabase Auth 和 PostgREST 的最小客户端。
 *
 * 教学项目没有引入大型网络框架，便于直接观察 URL、Header、JSON 和状态码；生产项目
 * 可以换成 Ktor 或 Retrofit，并保留相同的仓库层接口。
 */
class SupabaseClient(
    baseUrl: String,
    private val publishableKey: String,
    private val sessionStore: SessionStore,
) {
    private val baseUrl = baseUrl.trimEnd('/')

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && publishableKey.isNotBlank()

    suspend fun login(email: String, password: String): SupabaseSession = withContext(Dispatchers.IO) {
        checkConfigured()
        val result = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=password",
            body = JSONObject().put("email", email).put("password", password).toString(),
        )
        if (result.code !in 200..299) throw SupabaseException(result.message())
        parseSession(result.body).also(sessionStore::save)
    }

    /** upsert 保证重复登录或重复同步不会创建多个相同设备。 */
    suspend fun ensureDevice(deviceId: String, name: String) = withContext(Dispatchers.IO) {
        authorizedRequest(
            method = "POST",
            path = "/rest/v1/devices?on_conflict=id",
            body = JSONObject().put("id", deviceId).put("name", name).toString(),
            prefer = "resolution=merge-duplicates,return=minimal",
        ).requireSuccess()
    }

    /** 把 Room 实体转换为云端字段，并以设备 ID + 消息指纹保证幂等。 */
    suspend fun upload(deviceId: String, message: SmsEntity) = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("device_id", deviceId)
            .put("client_message_id", message.clientMessageId)
            .put("sender", message.sender)
            .put("body", message.body)
            .put("received_at", Instant.ofEpochMilli(message.receivedAt).toString())
            .apply {
                message.subscriptionId?.let { put("subscription_id", it) }
                message.simSlot?.let { put("sim_slot", it) }
            }
        authorizedRequest(
            method = "POST",
            path = "/rest/v1/sms_messages?on_conflict=device_id,client_message_id",
            body = payload.toString(),
            prefer = "resolution=ignore-duplicates,return=minimal",
        ).requireSuccess()
    }

    private fun authorizedRequest(
        method: String,
        path: String,
        body: String?,
        prefer: String? = null,
    ): HttpResult {
        var session = currentSession()
        var result = request(method, path, body, session.accessToken, prefer)
        if (result.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            // Access Token 失效时刷新一次并重放原请求，避免让每个调用者重复处理 401。
            session = refresh(session.refreshToken)
            result = request(method, path, body, session.accessToken, prefer)
        }
        return result
    }

    private fun currentSession(): SupabaseSession {
        val session = sessionStore.get() ?: throw SignedOutException()
        val now = System.currentTimeMillis() / 1000
        return if (session.expiresAtEpochSeconds <= now + 30) refresh(session.refreshToken) else session
    }

    private fun refresh(refreshToken: String): SupabaseSession {
        val result = request(
            method = "POST",
            path = "/auth/v1/token?grant_type=refresh_token",
            body = JSONObject().put("refresh_token", refreshToken).toString(),
        )
        if (result.code !in 200..299) {
            sessionStore.clear()
            throw SignedOutException(result.message())
        }
        return parseSession(result.body).also(sessionStore::save)
    }

    private fun parseSession(body: String): SupabaseSession {
        val json = JSONObject(body)
        val expiresAt = json.optLong("expires_at").takeIf { it > 0 }
            ?: (System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
        return SupabaseSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAtEpochSeconds = expiresAt,
        )
    }

    private fun request(
        method: String,
        path: String,
        body: String?,
        bearerToken: String? = null,
        prefer: String? = null,
    ): HttpResult {
        checkConfigured()
        val connection = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 15_000
            // apikey 识别 Supabase 项目；Bearer Token 识别当前登录用户，RLS 使用后者授权。
            setRequestProperty("apikey", publishableKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            bearerToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            prefer?.let { setRequestProperty("Prefer", it) }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }

    private fun checkConfigured() {
        check(isConfigured) { "尚未配置 Supabase URL 和 Publishable key" }
    }
}

private data class HttpResult(val code: Int, val body: String) {
    fun requireSuccess() {
        if (code !in 200..299) throw SupabaseException(message())
    }

    fun message(): String = runCatching {
        val json = JSONObject(body)
        json.optString("message").ifBlank { json.optString("error_description") }
            .ifBlank { json.optString("hint") }
            .ifBlank { body }
    }.getOrDefault(body).ifBlank { "Supabase 请求失败（HTTP $code）" }
}

class SupabaseException(message: String) : Exception(message)
class SignedOutException(message: String = "请重新登录") : Exception(message)
