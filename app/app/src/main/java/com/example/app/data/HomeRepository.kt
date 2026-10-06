package com.example.app.data

class HomeRepository(private val api: ApiClient) {
    suspend fun groups(token: String) = api.groups(token)
    suspend fun createGroup(token: String, name: String) = api.createGroup(token, name)
    suspend fun devices(token: String, groupId: String? = null) = api.devices(token, groupId)
    suspend fun addDevice(token: String, groupId: String, name: String, homieId: String) = api.addDevice(token, groupId, name, homieId)
    suspend fun deleteGroup(token: String, groupId: String) = api.deleteGroup(token, groupId)
    suspend fun deleteDevice(token: String, deviceId: String) = api.deleteDevice(token, deviceId)
}
