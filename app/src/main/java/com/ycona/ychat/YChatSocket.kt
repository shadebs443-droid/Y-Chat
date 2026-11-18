package com.ycona.ychat

import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class YChatSocket(private val onEvent: (JSONObject) -> Unit) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null
    @Volatile var connected = false
        private set

    fun connect(url: String, auth: JSONObject) {
        close()
        val request = Request.Builder().url(url).header("Origin", "https://ychat.local").build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(auth.toString())
            }
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.optString("status") == "ok" && json.has("token")) connected = true
                    onEvent(json)
                } catch (_: Exception) { }
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {}
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { connected = false; webSocket.close(code, reason) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { connected = false }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { connected = false; onEvent(JSONObject().put("type", "connection_error").put("msg", t.message ?: "Нет соединения")) }
        })
    }

    fun send(payload: JSONObject) { socket?.send(payload.toString()) }
    fun close() { connected = false; socket?.close(1000, "client close"); socket = null }
}
