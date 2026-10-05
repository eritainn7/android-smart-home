package com.example.app.mocks

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun register(email: String, password: String): Result<Unit>
}

class FakeAuthRepository : AuthRepository {
    override suspend fun login(email: String, password: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun register(email: String, password: String): Result<Unit> =
        Result.success(Unit)
}