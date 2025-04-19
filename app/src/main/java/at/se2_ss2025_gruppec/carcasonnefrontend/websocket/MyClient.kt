package at.se2_ss2025_gruppec.carcasonnefrontend.websocket

import android.os.Handler
import android.os.Looper
import android.util.Log
import at.se2_ss2025_gruppec.carcasonnefrontend.TokenManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.hildan.krossbow.stomp.StompClient
import org.hildan.krossbow.stomp.StompSession
import kotlinx.coroutines.launch
import org.hildan.krossbow.stomp.sendText
import org.hildan.krossbow.stomp.subscribeText
import org.hildan.krossbow.websocket.okhttp.OkHttpWebSocketClient
import org.json.JSONObject

class MyClient(val callbacks: Callbacks) {

    private val WEBSOCKET_URI = "ws://10.0.2.2:8080/ws/game/websocket" //Use local ip address for real device demo

    private lateinit var topicFlow: Flow<String>
    private lateinit var collector: Job

    private lateinit var jsonFlow: Flow<String>
    private lateinit var jsonCollector: Job

    private lateinit var client: StompClient
    private var session: StompSession? = null

    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private fun callback(msg: String) {
        Handler(Looper.getMainLooper()).post {
            callbacks.onResponse(msg)
        }
    }

    fun connect() {
        //Retrieve token from Singleton and append it to WebSocket request
        val token = TokenManager.userToken ?: throw IllegalStateException("Token is required, but was null!")
        client = StompClient(OkHttpWebSocketClient())

        scope.launch {
            try {
                session = client.connect(
                    WEBSOCKET_URI,
                    customStompConnectHeaders = mapOf("Authorization" to "Bearer $token")
                )
                callback("Login successful!")
            } catch (e: Exception) {
                Log.e("WebSocket", "Connection failed: ${e.message}")
                callback("Login failed: ${e.message}")
            }
        }
    }

    fun sendHello() {
        scope.launch {
            try {
                session?.sendText("/app/hello", "message from client")
                Log.d("WebSocket", "Message sent: Hello")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send hello: ${e.message}")
            }
        }
    }

    fun sendJson() {
        val json = JSONObject().apply {
            put("from", "client")
            put("text", "from client")
        }
        val message = json.toString()

        scope.launch {
            try {
                session?.sendText("/app/object", message)
                Log.d("WebSocket", "JSON message sent: $message")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send JSON: ${e.message}")
            }
        }
    }

    fun sendStartGame(gameId: String) {
        val json = JSONObject().apply {
            put("type", "start_game")
            put("gameId", gameId)
            put("player", "HOST") //Should be replaced with actual player ID later
        }

        scope.launch {
            try {
                session?.sendText("/app/game/send", json.toString())
                Log.d("WebSocket", "Start game message sent: $json")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send start game: ${e.message}")
            }
        }
    }

    fun listenOn(topic: String, onMessage: (String) -> Unit) {
        scope.launch {
            try {
                val activeSession = session
                if (activeSession == null) {
                    Log.e("WebSocket", "Cannot listen - session not initialized")
                    return@launch
                }
                val flow = activeSession.subscribeText(topic)
                flow.collect { msg ->
                    Log.d("WebSocket", "Received message on $topic: $msg")
                    onMessage(msg)
                }
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to listen on $topic: ${e.message}")
            }
        }
    }
    fun isConnected(): Boolean {
        return session != null
    }
    fun sendJoinGame(gameId: String, playerName: String) {
        val json = JSONObject().apply {
            put("type", "join_game")
            put("gameId", gameId)
            put("player", playerName)
        }

        scope.launch {
            try {
                session?.sendText("/app/game/send", json.toString())
                Log.d("WebSocket", "Join game message sent: $json")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send join game: ${e.message}")
            }
        }
    }
}