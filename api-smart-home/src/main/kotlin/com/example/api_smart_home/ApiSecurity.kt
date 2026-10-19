package com.example.api_smart_home

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets

@Component
class JwtUserResolver(@Value("\${app.jwt.secret}") secret: String) {
    private val key = Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))
    fun userId(authorization: String?): String {
        val token = authorization?.takeIf { it.startsWith("Bearer ", true) }?.substringAfter(' ')
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token required")
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token).payload.subject
                ?: throw IllegalArgumentException("No subject")
        } catch (ex: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token")
        }
    }
}
