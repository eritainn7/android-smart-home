package com.example.api_smart_home

import org.springframework.integration.mqtt.support.MqttHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.MessageHandler
import org.springframework.stereotype.Component

@Component
class MqttTelemetryHandler(private val devices: DeviceService) : MessageHandler {
    override fun handleMessage(message: Message<*>) {
        val topic = message.headers[MqttHeaders.RECEIVED_TOPIC]?.toString() ?: return
        // Homie: homie/{deviceId}/{nodeId}/{propertyId}; metadata messages are ignored.
        val parts = topic.split('/')
        val dollar = 36.toChar()
        if (parts.size != 4 || parts[0] != "homie" || parts[2].startsWith(dollar)) return
        val property = parts[3]
        if (property.startsWith(dollar)) return
        devices.acceptTelemetry(parts[1], property, message.payload?.toString() ?: return)
    }
}
