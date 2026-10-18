package com.example.auth

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping
class AuthController(private val auth: AuthService) {
    @PostMapping("/auth/reg")
    fun register(@Valid @RequestBody request: RegisterRequest) = auth.register(request.email, request.password)

    @PostMapping("/auth/confirm")
    fun confirm(@Valid @RequestBody request: ConfirmRequest) = auth.confirm(request.email, request.code)

    @PostMapping("/auth/login")
    fun login(@Valid @RequestBody request: LoginRequest) = auth.login(request.email, request.password)

    @PostMapping("/auth/logout")
    fun logout(@RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String): Map<String, Boolean> {
        auth.logout(bearer(authorization)); return mapOf("success" to true)
    }

    @DeleteMapping("/auth/login")
    fun delete(@RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String): Map<String, Boolean> {
        auth.deleteAccount(bearer(authorization)); return mapOf("success" to true)
    }

    @PutMapping("/auth/login")
    fun update(@RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String, @Valid @RequestBody request: UpdateAccountRequest) =
        auth.updateAccount(bearer(authorization), request.email, request.password)

    @PostMapping("/validate")
    fun validate(@RequestHeader(HttpHeaders.AUTHORIZATION) authorization: String) = auth.validate(bearer(authorization))

    private fun bearer(value: String): String {
        if (!value.startsWith("Bearer ", ignoreCase = true)) throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Authorization must use Bearer token")
        return value.substringAfter(' ').trim().also { if (it.isBlank()) throw org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Bearer token is missing") }
    }
}

data class RegisterRequest(@field:Email @field:NotBlank val email: String, @field:NotBlank @field:Size(min = 10, max = 72) val password: String)
data class LoginRequest(@field:Email @field:NotBlank val email: String, @field:NotBlank val password: String)
data class ConfirmRequest(@field:Email @field:NotBlank val email: String, @field:NotBlank @field:Size(min = 6, max = 6) @field:jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}") val code: String)
data class UpdateAccountRequest(@field:Email val email: String? = null, @field:Size(min = 10, max = 72) val password: String? = null)
