package com.example.api_smart_home

import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.config.EnableIntegration
import org.springframework.integration.channel.DirectChannel
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.MessageHandler

@Configuration
@EnableIntegration
class MqttConfig(
    @Value("\${app.mqtt.enabled:true}") private val enabled: Boolean,
    @Value("\${app.mqtt.url}") private val url: String,
    @Value("\${app.mqtt.client-id}") private val clientId: String,
    @Value("\${app.mqtt.username:}") private val username: String,
    @Value("\${app.mqtt.password:}") private val password: String,
    @Value("\${app.mqtt.telemetry-topic}") private val telemetryTopic: String,
) {
    @Bean
    fun mqttClientFactory(): DefaultMqttPahoClientFactory = DefaultMqttPahoClientFactory().also { factory ->
        factory.connectionOptions = MqttConnectOptions().apply {
            serverURIs = arrayOf(url)
            isAutomaticReconnect = true
            isCleanSession = false
            connectionTimeout = 10
            keepAliveInterval = 30
            if (this@MqttConfig.username.isNotBlank()) setUserName(this@MqttConfig.username)
            if (this@MqttConfig.password.isNotBlank()) setPassword(this@MqttConfig.password.toCharArray())
        }
    }

    @Bean fun mqttInputChannel(): MessageChannel = DirectChannel()
    @Bean fun mqttOutboundChannel(): MessageChannel = DirectChannel()

    @Bean
    fun mqttInboundAdapter(factory: DefaultMqttPahoClientFactory): MqttPahoMessageDrivenChannelAdapter? {
        if (!enabled) return null
        return MqttPahoMessageDrivenChannelAdapter("$clientId-in", factory, telemetryTopic).apply {
            setCompletionTimeout(5000)
            setConverter(DefaultPahoMessageConverter())
            setQos(1)
            setOutputChannel(mqttInputChannel())
        }
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    fun mqttInboundHandler(handler: MqttTelemetryHandler): MessageHandler = handler

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    fun mqttOutboundHandler(factory: DefaultMqttPahoClientFactory): MessageHandler =
        MqttPahoMessageHandler("$clientId-out", factory).apply {
            setAsync(true)
            setDefaultQos(1)
        }
}
