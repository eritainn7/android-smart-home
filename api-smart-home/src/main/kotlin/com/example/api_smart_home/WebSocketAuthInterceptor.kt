package com.example.api_smart_home

import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.stereotype.Component
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor
import java.util.UUID

@Component
class WebSocketAuthInterceptor(private val resolver: JwtUserResolver) : HandshakeInterceptor {
    override fun beforeHandshake(request: ServerHttpRequest, response: ServerHttpResponse, wsHandler: WebSocketHandler, attributes: MutableMap<String, Any>): Boolean {
        // OkHttp/native clients can set Authorization on the handshake. Avoid query-string tokens,
        // which are commonly retained in access logs and proxy telemetry.
        val authorization = request.headers.getFirst("Authorization") ?: return false
        return try {
            attributes["userId"] = UUID.fromString(resolver.userId(authorization)).toString()
            true
        } catch (_: Exception) { false }
    }
    override fun afterHandshake(request: ServerHttpRequest, response: ServerHttpResponse, wsHandler: WebSocketHandler, exception: Exception?) = Unit
}
