package com.example.app.data

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class DeviceEvents(private val client: OkHttpClient = OkHttpClient.Builder().pingInterval(25, TimeUnit.SECONDS).build()) {
    fun connect(apiBaseUrl: String, token: String): Flow<String> = callbackFlow {
        val socketRef = AtomicReference<WebSocket?>(null)
        val messages = Channel<String>(Channel.BUFFERED)
        val url = apiBaseUrl.replaceFirst("http", "ws").trimEnd('/') + "/ws/devices"
        val sender = launch {
            for (message in messages) trySend(message)
        }
        val reconnector = launch {
            while (isActive) {
                val request = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
                val done = Channel<Unit>(Channel.CONFLATED)
                val socket = client.newWebSocket(request, object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) { messages.trySend(text) }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { done.trySend(Unit) }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { done.trySend(Unit) }
                })
                socketRef.set(socket)
                done.receive()
                done.close()
                socket.cancel()
                if (isActive) delay(1500)
            }
        }
        awaitClose {
            reconnector.cancel()
            sender.cancel()
            messages.close()
            socketRef.getAndSet(null)?.close(1000, "screen closed")
        }
    }
}
