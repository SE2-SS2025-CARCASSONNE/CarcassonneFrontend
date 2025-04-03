package at.se2_ss2025_gruppec.carcasonnefrontend.websocket

import android.os.Handler
import android.os.Looper
import android.util.Log
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

class StompClient(val callbacks: Callbacks) {

    private  val WEBSOCKET_URI = "ws://10.0.2.2:8080/ws/game/websocket"

    private lateinit var topicFlow: Flow<String>
    private lateinit var collector: Job

    private lateinit var jsonFlow: Flow<String>
    private lateinit var jsonCollector:Job

    private lateinit var client:StompClient
    private lateinit var session: StompSession

    private val scope:CoroutineScope=CoroutineScope(Dispatchers.IO)

    private fun callback(msg:String){
        Handler(Looper.getMainLooper()).post{
            Log.d("WebSocket", "Message received: $msg")
            callbacks.onResponse(msg)
        }
    }

    fun connect() {

        client = StompClient(OkHttpWebSocketClient()) // other config can be passed in here
        scope.launch {
            try {
                session = client.connect(WEBSOCKET_URI)
                topicFlow= session.subscribeText("/topic/hello-response")
                //connect to topic
                collector=scope.launch {
                    topicFlow.collect{
                        msg->
                    //todo logic
                    callback(msg)
                    }
                }

                //connect to topic
                jsonFlow= session.subscribeText("/topic/rcv-object")
                jsonCollector=scope.launch {
                    jsonFlow.collect{
                        msg->
                    var jsonObject= JSONObject(msg)
                    callback(jsonObject.getString("text"))
                    }
                }
                callback("connected")
            } catch (e: Exception) {
                Log.e("WebSocket", "Connection failed: ${e.message}")
                callback("Connection failed: ${e.message}")
            }
        }

    }

    fun sendHello(){
        scope.launch {

            try {
                session.sendText("/app/hello","message from client")
                Log.d("WebSocket","Message sent: Hello")
            } catch (e: Exception) {
                Log.d("WebSocket","Message sent: Hello")
            }

        }
    }

    fun sendJson(){
        var json=JSONObject().apply {
            put("from", "client")
            put("text", "from client")
        }
        val message = json.toString()

        scope.launch {
            try {
                session.sendText("/app/object", message);
                Log.d("WebSocket", "JSON message sent: $message")

            } catch (e: Exception){
                Log.e("WebSocket", "Failed to send JSON: ${e.message}")
            }
        }
    }

}