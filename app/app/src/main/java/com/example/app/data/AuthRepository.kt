package com.example.app.data

import org.json.JSONObject

class AuthRepository(private val api: ApiClient) {
    suspend fun login(email: String, password: String): String = api.login(email, password)
    suspend fun register(email: String, password: String): JSONObject = api.register(email, password)
    suspend fun confirm(email: String, code: String): JSONObject = api.confirm(email, code)
}
