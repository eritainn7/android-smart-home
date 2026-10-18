package com.example.auth

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class AuthService(
    private val jdbc: JdbcTemplate,
    private val redis: StringRedisTemplate,
    @Value("\${app.jwt.secret}") jwtSecret: String,
    @Value("\${app.jwt.ttl-seconds:3600}") private val jwtTtlSeconds: Long,
    @Value("\${app.registration.code-ttl-seconds:600}") private val codeTtlSeconds: Long,
    @Value("\${app.registration.require-email-confirmation:true}") private val requireEmailConfirmation: Boolean,
    @Value("\${app.mail.from:no-reply@localhost}") private val mailFrom: String,
    @Value("\${spring.mail.host:}") private val mailHost: String,
    private val mailSender: JavaMailSender,
) {
    private val encoder = BCryptPasswordEncoder(12)
    private val secureRandom = SecureRandom()
    private val signingKey = Keys.hmacShaKeyFor(jwtSecret.toByteArray(StandardCharsets.UTF_8))

    init {
        require(jwtTtlSeconds in 60..86400) { "JWT TTL must be between 60 and 86400 seconds" }
        require(codeTtlSeconds in 60..3600) { "Registration code TTL must be between 60 and 3600 seconds" }
    }

    fun register(emailInput: String, password: String): RegistrationResult {
        val email = normalizeEmail(emailInput)
        validatePassword(password)
        if (exists(email)) throw ResponseStatusException(HttpStatus.CONFLICT, "Аккаунт с таким email уже существует")
        val id = UUID.randomUUID()
        if (requireEmailConfirmation) {
            val code = "%06d".format(secureRandom.nextInt(1_000_000))
            val codeHash = sha256(code)
            if (mailHost.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "SMTP is not configured")
            val key = "registration:$email"
            val attemptsKey = "registration-attempts:$email"
            // A new code starts a new attempt window; persist only a hashed code and bcrypt password.
            redis.delete(attemptsKey)
            redis.opsForValue().set(key, "$id:${encoder.encode(password)}:$codeHash", codeTtlSeconds, TimeUnit.SECONDS)
            try {
                val message = SimpleMailMessage().apply {
                    setFrom(mailFrom)
                    setTo(email)
                    subject = "Код подтверждения регистрации"
                    text = "Ваш код подтверждения: $code\n\nСрок действия кода — ${codeTtlSeconds / 60} минут. Если вы не запрашивали регистрацию, проигнорируйте это письмо."
                }
                mailSender.send(message)
            } catch (ex: Exception) {
                redis.delete(listOf(key, "registration-attempts:$email"))
                throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Не удалось отправить письмо подтверждения")
            }
            return RegistrationResult(email, "Код подтверждения отправлен на email")
        }
        try {
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,?)", id, email, encoder.encode(password))
        } catch (ex: org.springframework.dao.DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Аккаунт с таким email уже существует")
        }
        return RegistrationResult(email, "Аккаунт создан")
    }

    fun confirm(emailInput: String, code: String): RegistrationResult {
        if (!requireEmailConfirmation) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Подтверждение email не включено")
        val email = normalizeEmail(emailInput)
        val key = "registration:$email"
        val value = redis.opsForValue().get(key) ?: throw ResponseStatusException(HttpStatus.GONE, "Код истёк или не найден")
        val attemptsKey = "registration-attempts:$email"
        if (redis.getExpire(attemptsKey, TimeUnit.SECONDS) <= 0) redis.delete(attemptsKey)
        val attempts = redis.opsForValue().increment(attemptsKey) ?: 1L
        if (attempts == 1L) redis.expire(attemptsKey, codeTtlSeconds, TimeUnit.SECONDS)
        if (attempts > 5) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Слишком много попыток; запросите новый код позже")
        val parts = value.split(":", limit = 3)
        if (parts.size != 3 || !MessageDigest.isEqual(parts[2].toByteArray(), sha256(code).toByteArray())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Неверный код")
        }
        try {
            jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,?)", UUID.fromString(parts[0]), email, parts[1])
        } catch (ex: org.springframework.dao.DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Аккаунт с таким email уже существует")
        }
        redis.delete(listOf(key, attemptsKey))
        return RegistrationResult(email, "Email подтверждён")
    }

    fun login(emailInput: String, password: String): TokenResponse {
        val email = normalizeEmail(emailInput)
        val row = jdbc.query("SELECT id,password_hash FROM users WHERE email=?", { rs, _ -> rs.getObject("id", UUID::class.java) to rs.getString("password_hash") }, email)
            .firstOrNull() ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный email или пароль")
        if (!encoder.matches(password, row.second)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Неверный email или пароль")
        val now = Instant.now()
        val token = Jwts.builder().subject(row.first.toString()).claim("email", email)
            .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(jwtTtlSeconds))).signWith(signingKey).compact()
        return TokenResponse(token, "Bearer", jwtTtlSeconds)
    }

    fun logout(token: String) {
        val claims = parse(token)
        val ttl = (claims.expiration.toInstant().epochSecond - Instant.now().epochSecond).coerceAtLeast(1)
        redis.opsForValue().set("revoked:${sha256(token)}", "1", ttl, TimeUnit.SECONDS)
    }

    fun validate(token: String): ValidationResponse {
        val claims = parse(token)
        if (redis.hasKey("revoked:${sha256(token)}") == true) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Токен отозван")
        return ValidationResponse(true, claims.subject, claims["email"].toString(), claims.expiration.toInstant().toString())
    }

    @Transactional
    fun deleteAccount(token: String) {
        val claims = validate(token)
        redis.opsForValue().set("revoked:${sha256(token)}", "1", jwtTtlSeconds, TimeUnit.SECONDS)
        jdbc.update("DELETE FROM users WHERE id=?", UUID.fromString(claims.userId))
    }

    @Transactional
    fun updateAccount(token: String, newEmailInput: String?, newPassword: String?): AccountResponse {
        val claims = validate(token)
        val newEmail = newEmailInput?.let(::normalizeEmail)
        if (newEmail == claims.email && newPassword == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет изменений")
        newPassword?.let(::validatePassword)
        if (newEmail == null && newPassword == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Нет изменений")
        try {
            when {
                newEmail != null && newPassword != null -> jdbc.update("UPDATE users SET email=?,password_hash=? WHERE id=?", newEmail, encoder.encode(newPassword), UUID.fromString(claims.userId))
                newEmail != null -> jdbc.update("UPDATE users SET email=? WHERE id=?", newEmail, UUID.fromString(claims.userId))
                else -> jdbc.update("UPDATE users SET password_hash=? WHERE id=?", encoder.encode(newPassword!!), UUID.fromString(claims.userId))
            }
        } catch (ex: org.springframework.dao.DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Email уже используется")
        }
        logout(token)
        return AccountResponse(newEmail ?: claims.email)
    }

    private fun parse(token: String) = try {
        Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).payload
    } catch (ex: Exception) {
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Недействительный или просроченный токен")
    }

    private fun normalizeEmail(value: String) = value.trim().lowercase().also {
        if (it.length > 320 || !it.matches(Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный email")
    }

    private fun validatePassword(password: String) {
        if (password.length !in 10..72 || password.any { it.code < 32 }) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Пароль должен содержать от 10 до 72 символов")
    }

    private fun exists(email: String) = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE email=?)", Boolean::class.java, email) ?: false
    private fun sha256(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}

data class RegistrationResult(val email: String, val message: String)
data class TokenResponse(val accessToken: String, val tokenType: String, val expiresIn: Long)
data class ValidationResponse(val valid: Boolean, val userId: String, val email: String, val expiresAt: String)
data class AccountResponse(val email: String)
