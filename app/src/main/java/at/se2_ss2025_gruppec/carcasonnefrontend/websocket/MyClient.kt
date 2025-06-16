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
import okhttp3.OkHttpClient
import org.hildan.krossbow.stomp.sendText
import org.hildan.krossbow.stomp.subscribeText
import org.hildan.krossbow.websocket.okhttp.OkHttpWebSocketClient
import org.json.JSONObject
import at.se2_ss2025_gruppec.carcasonnefrontend.model.dto.MeepleDto


class MyClient(val callbacks: Callbacks) {

    private val WEBSOCKET_URI = "ws://10.0.2.2:8080/ws/game" // Enter your local IP address instead of localhost (10.0.2.2) for real device demo!
    //private val WEBSOCKET_URI = "ws://192.168.8.54:8080/ws/game"

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
        val token = TokenManager.userToken ?: throw IllegalStateException("Token is required, but was null!")
        Log.d("WebSocket", "Attempting WebSocket connection with token: Bearer $token")

        // Custom OkHttpClient that attaches JWT to auth header of HTTP upgrade request
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                Log.d("WebSocket", "Adding Authorization header to handshake: ${request.headers}")
                chain.proceed(request)
            }
            .build()

        // Create STOMP client with the custom OkHttpClient
        client = StompClient(OkHttpWebSocketClient(okHttpClient))

        // Launch connection in coroutine
        scope.launch {
            try {
                session = client.connect(
                    WEBSOCKET_URI,
                    customStompConnectHeaders = mapOf("Authorization" to "Bearer $token")
                )
                Log.d("WebSocket", "WebSocket connection established!")
                callback("Login successful!")
            } catch (e: Exception) {
                Log.e("WebSocket", "WebSocket connection failed: ${e.message}")
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

    fun sendStartGame(gameId: String,playerId: String) {
        val json = JSONObject().apply {
            put("type", "start_game")
            put("gameId", gameId)
            put("player", playerId)
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
                val activeSession = session ?: return@launch
                val flow = activeSession.subscribeText(topic)

                flow.collect { msg ->
                    Log.d("WebSocket", "Received message on $topic: $msg")
                    onMessage(msg) // Forward the full raw message to handler (e.g., handleLobbyMessage)
                }

            } catch (e: Exception) {
                Log.e("WebSocket", "❌ Failed to listen on $topic: ${e.message}")
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
    fun sendDrawTileRequest(gameId: String, playerId: String) {
        val json = JSONObject().apply {
            put("type", "DRAW_TILE")
            put("gameId", gameId)
            put("player", playerId)
        }

        scope.launch {
            try {
                session?.sendText("/app/game/send", json.toString())
                Log.d("WebSocket", "Draw tile request sent: $json")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send draw tile request: ${e.message}")
            }
        }
    }


    fun sendPlaceMeeple(gameId: String, playerId: String, meepleId: String, tileId: String, position: String) {
        val json = JSONObject().apply {
            put("type", "place_meeple")
            put("gameId", gameId)
            put("player", playerId)
            put("meeple", JSONObject().apply {
                put("id", meepleId)
                put("playerId", playerId)
                // put("type", "MONK") // TODO MIKE: Sollte entfallen und rein im Backend gelöst werden.
                put("tileId", tileId)
                put("position", position)
            })
        }

        scope.launch {
            try {
                session?.sendText("/app/game/send", json.toString())
                Log.d("WebSocket", "Meeple placement message sent: $json")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send meeple placement: ${e.message}")
            }
        }
    }
    fun sendPlaceTileRequest(payload: String){
        scope.launch {
            try {
                session?.sendText("/app/game/send", payload)
                Log.d("WebSocket", "Tile placement request sent: $payload")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send tile placement request: ${e.message}")

            }
        }
    }

    fun sendCalculateScoreRequest(gameId: String) {
        val json = JSONObject().apply {
            put("type", "calculate_score")
            put("gameId", gameId)
        }

        scope.launch {
            try {
                session?.sendText("/app/game/send", json.toString())
                Log.d("WebSocket", "Calculate score request sent: $json")
            } catch (e: Exception) {
                Log.e("WebSocket", "Failed to send calculate score request: ${e.message}")
            }
        }
    }
}