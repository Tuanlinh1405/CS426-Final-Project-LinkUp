package com.example.linkup.data.remote.websocket

import android.util.Log
import com.example.linkup.data.local.datastore.AuthTokenDataStore
import com.example.linkup.data.remote.dto.MessageDto
import com.example.linkup.data.remote.dto.WebSocketFrameDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatWebSocketClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authTokenDataStore: AuthTokenDataStore,
    private val json: Json,
    private val baseUrl: String = "http://10.0.2.2:8080/",
) {
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null
    private var shouldKeepAlive = false
    private var connectedToken: String? = null

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingFrames = MutableSharedFlow<WebSocketFrameDto>(extraBufferCapacity = 64)
    val incomingFrames: SharedFlow<WebSocketFrameDto> = _incomingFrames.asSharedFlow()

    fun connect() {
        shouldKeepAlive = true
        reconnectJob?.cancel()

        scope.launch {
            val token = authTokenDataStore.getStoredToken()
            if (token.isNullOrEmpty()) {
                Log.w(TAG, "Cannot connect to WebSocket: JWT token is missing")
                _connectionState.value = ConnectionState.DISCONNECTED
                return@launch
            }

            if (_connectionState.value == ConnectionState.CONNECTED && connectedToken == token) {
                return@launch
            }

            if (_connectionState.value != ConnectionState.DISCONNECTED) {
                webSocket?.close(1000, "Reconnecting with new token")
                webSocket = null
            }

            _connectionState.value = ConnectionState.CONNECTING
            connectedToken = token

            val wsUrl = buildWebSocketUrl(baseUrl, token)
            val request = Request.Builder()
                .url(wsUrl)
                .build()

            Log.d(TAG, "Connecting to WebSocket: $wsUrl")
            webSocket = okHttpClient.newWebSocket(request, createWebSocketListener())
        }
    }

    fun disconnect() {
        shouldKeepAlive = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "User disconnected")
        webSocket = null
        connectedToken = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    fun sendFrame(frame: WebSocketFrameDto): Boolean {
        val currentWs = webSocket
        if (_connectionState.value != ConnectionState.CONNECTED || currentWs == null) {
            Log.w(TAG, "Cannot send frame, WebSocket is not connected")
            return false
        }

        return try {
            val text = json.encodeToString(frame)
            currentWs.send(text)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding or sending frame", e)
            false
        }
    }

    fun sendMessage(
        conversationId: String,
        textContent: String?,
        type: String = "TEXT",
        tempId: String? = null,
    ): Boolean {
        val frame = WebSocketFrameDto(
            event = "SEND_MESSAGE",
            conversationId = conversationId,
            message = MessageDto(
                conversationId = conversationId,
                type = type,
                textContent = textContent,
            ),
            tempId = tempId
        )
        return sendFrame(frame)
    }

    fun markRead(conversationId: String): Boolean {
        val frame = WebSocketFrameDto(
            event = "MARK_READ",
            conversationId = conversationId
        )
        return sendFrame(frame)
    }

    fun sendTyping(conversationId: String, isTyping: Boolean): Boolean {
        val frame = WebSocketFrameDto(
            event = "TYPING",
            conversationId = conversationId,
            isTyping = isTyping
        )
        return sendFrame(frame)
    }

    private fun createWebSocketListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected successfully")
            _connectionState.value = ConnectionState.CONNECTED
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "WebSocket message received: $text")
            try {
                val frame = json.decodeFromString<WebSocketFrameDto>(text)
                _incomingFrames.tryEmit(frame)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse incoming WS frame: $text", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closing: $code / $reason")
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code / $reason")
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket failure: ${t.message}", t)
            _connectionState.value = ConnectionState.DISCONNECTED
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (!shouldKeepAlive) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(2000)
            if (shouldKeepAlive && _connectionState.value == ConnectionState.DISCONNECTED) {
                Log.d(TAG, "Attempting WebSocket auto-reconnect...")
                connect()
            }
        }
    }

    private fun buildWebSocketUrl(httpBaseUrl: String, token: String): String {
        val normalizedBase = httpBaseUrl
            .replace("^http://".toRegex(), "ws://")
            .replace("^https://".toRegex(), "wss://")
            .trimEnd('/')

        return "$normalizedBase/chat/ws?token=$token"
    }

    companion object {
        private const val TAG = "ChatWebSocketClient"
    }
}
