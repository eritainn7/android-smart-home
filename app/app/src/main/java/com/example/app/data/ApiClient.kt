package com.example.app.data

import com.example.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(
    private val authBaseUrl: String = BuildConfig.AUTH_BASE_URL,
    private val apiBaseUrl: String = BuildConfig.API_BASE_URL
) {
    suspend fun register(email: String, password: String): JSONObject = request(authBaseUrl, "/auth/reg", "POST", body = JSONObject().put("email", email).put("password", password))
    suspend fun confirm(email: String, code: String): JSONObject = request(authBaseUrl, "/auth/confirm", "POST", body = JSONObject().put("email", email).put("code", code))
    suspend fun login(email: String, password: String): String = request(authBaseUrl, "/auth/login", "POST", body = JSONObject().put("email", email).put("password", password)).getString("accessToken")
    suspend fun groups(token: String): List<HomeGroup> = requestArray(apiBaseUrl, "/api/groups", token).toGroups()
    suspend fun createGroup(token: String, name: String): HomeGroup = request(apiBaseUrl, "/api/groups", "POST", token, JSONObject().put("name", name)).let { HomeGroup(it.getString("id"), it.getString("name")) }
    suspend fun devices(token: String, groupId: String? = null): List<HomeDevice> = requestArray(apiBaseUrl, "/api/devices" + (groupId?.let { "?groupId=$it" } ?: ""), token).toDevices()
    suspend fun addDevice(token: String, groupId: String, name: String, homieDeviceId: String): HomeDevice = request(apiBaseUrl, "/api/groups/$groupId/devices", "POST", token, JSONObject().put("name", name).put("homieDeviceId", homieDeviceId)).toDevice()
    suspend fun deleteGroup(token: String, id: String) { request(apiBaseUrl, "/api/groups/$id", "DELETE", token) }
    suspend fun deleteDevice(token: String, id: String) { request(apiBaseUrl, "/api/devices/$id", "DELETE", token) }

    private suspend fun requestArray(base: String, path: String, token: String): JSONArray = withContext(Dispatchers.IO) {
        val connection = connection(base, path, "GET", token, null)
        try {
            val (status, text) = read(connection)
            if (status !in 200..299) throw ApiException(errorMessage(text, status), status)
            JSONArray(text)
        } finally { connection.disconnect() }
    }

    private suspend fun request(base: String, path: String, method: String, token: String? = null, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = connection(base, path, method, token, body)
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val (status, text) = read(connection)
            if (status !in 200..299) throw ApiException(errorMessage(text, status), status)
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally { connection.disconnect() }
    }

    private fun connection(base: String, path: String, method: String, token: String?, body: JSONObject?) =
        (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 8000; readTimeout = 12000
            setRequestProperty("Accept", "application/json")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json; charset=utf-8") }
        }

    private fun read(connection: HttpURLConnection): Pair<Int, String> {
        val status = connection.responseCode
        if (status == HttpURLConnection.HTTP_NO_CONTENT) return status to ""
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return status to (stream?.bufferedReader()?.use { it.readText() } ?: "")
    }

    private fun errorMessage(text: String, status: Int) = runCatching {
        val json = JSONObject(text)
        json.optString("detail").ifBlank { json.optString("message") }.ifBlank { "Ошибка сервера ($status)" }
    }.getOrDefault("Ошибка сервера ($status)")
}

data class ApiException(override val message: String, val status: Int) : Exception(message)
data class HomeGroup(val id: String, val name: String)
data class HomeDevice(val id: String, val groupId: String, val name: String, val homieDeviceId: String, val properties: JSONObject)

private fun JSONArray.toGroups(): List<HomeGroup> = (0 until length()).map { getJSONObject(it).let { obj -> HomeGroup(obj.getString("id"), obj.getString("name")) } }
private fun JSONArray.toDevices(): List<HomeDevice> = (0 until length()).map { getJSONObject(it).toDevice() }
private fun JSONObject.toDevice() = HomeDevice(getString("id"), getString("groupId"), getString("name"), getString("homieDeviceId"), optJSONObject("properties") ?: JSONObject())
