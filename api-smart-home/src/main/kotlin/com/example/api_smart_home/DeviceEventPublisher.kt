package com.example.api_smart_home

import tools.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.integration.mqtt.support.MqttHeaders
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.support.MessageBuilder
import org.springframework.stereotype.Component
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

@Component
class DeviceEventPublisher(
    private val handler: DeviceWebSocketHandler,
    @Qualifier("mqttOutboundChannel") private val mqttOutput: MessageChannel
) {
    fun publishTelemetry(ownerId: String, event: Map<String, Any>) = handler.sendToUser(ownerId, mapOf("type" to "telemetry", "data" to event))
    fun publishCommand(homieId: String, property: String, value: String) {
        mqttOutput.send(MessageBuilder.withPayload(value).setHeader(MqttHeaders.TOPIC, "homie/$homieId/sensor/$property/set").build())
    }
}

@Component
class DeviceWebSocketHandler(private val mapper: ObjectMapper) : TextWebSocketHandler() {
    private val sessions = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val ownerId = session.attributes["userId"] as? String ?: run { session.close(); return }
        sessions.computeIfAbsent(ownerId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: org.springframework.web.socket.CloseStatus) {
        sessions.values.forEach { it.remove(session) }
    }

    fun sendToUser(ownerId: String, payload: Map<String, Any>) {
        val message = TextMessage(mapper.writeValueAsString(payload))
        sessions[ownerId]?.toList()?.forEach { session ->
            try { if (session.isOpen) session.sendMessage(message) } catch (_: Exception) { sessions[ownerId]?.remove(session) }
        }
    }
}

@org.springframework.context.annotation.Configuration
@org.springframework.web.socket.config.annotation.EnableWebSocket
class WebSocketConfig(
    private val handler: DeviceWebSocketHandler,
    private val auth: WebSocketAuthInterceptor,
    @org.springframework.beans.factory.annotation.Value("\${app.websocket.allowed-origins:*}") origins: String
) : org.springframework.web.socket.config.annotation.WebSocketConfigurer {
    private val allowed = origins.split(',').map(String::trim).toTypedArray()
    override fun registerWebSocketHandlers(registry: org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry) {
        registry.addHandler(handler, "/ws/devices").addInterceptors(auth).setAllowedOriginPatterns(*allowed)
    }
}
