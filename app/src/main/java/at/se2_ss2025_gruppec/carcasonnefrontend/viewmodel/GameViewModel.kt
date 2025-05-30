package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel


import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.Callbacks
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import org.json.JSONObject

enum class TileRotation {
    NORTH, EAST, SOUTH, WEST
}

data class Tile(
    val id: String,
    val top: String,
    val right: String,
    val bottom: String,
    val left: String,
    val hasShield: Boolean = false,
    val hasMonastery: Boolean = false,
    val tileRotation: TileRotation = TileRotation.NORTH
)

fun getDrawableNameForTile(tile: Tile): String {
    val baseId = tile.id.substringBefore("-")
    return baseId.replace("-", "_")
}

fun Tile.topColor(): Color = directionToColor(this.top)
fun Tile.rightColor(): Color = directionToColor(this.right)
fun Tile.bottomColor(): Color = directionToColor(this.bottom)
fun Tile.leftColor(): Color = directionToColor(this.left)

fun directionToColor(type: String): Color = when (type) {
    "ROAD" -> Color.Yellow
    "CITY" -> Color.Red
    "MONASTERY" -> Color.Blue
    "FIELD" -> Color.Green
    else -> Color.Gray
}

class GameViewModel : ViewModel() {

    private val _tileDeck = mutableStateListOf<Tile>()
    val tileDeck: List<Tile> = _tileDeck

    private val _currentTile = mutableStateOf<Tile?>(null)
    val currentTile: State<Tile?> get() = _currentTile

    private lateinit var webSocketClient: MyClient

    init {
        webSocketClient = MyClient(object : Callbacks {
            override fun onResponse(msg: String) {
                handleWebSocketMessage(msg)
            }
        })
        webSocketClient.connect()
    }

    fun subscribeToGame(gameId: String) {
        webSocketClient.listenOn("/topic/game/$gameId") { msg ->
            handleWebSocketMessage(msg)
        }
    }

    fun requestTileFromBackend(gameId: String, playerId: String) {
        if (webSocketClient.isConnected()) {
            webSocketClient.sendDrawTileRequest(gameId, playerId)
        } else {
            Log.e("WebSocket", "Not connected yet, can't draw tile.")
        }
    }
    private fun handleWebSocketMessage(msg: String) {
        try {
            val json = JSONObject(msg)
            val type = json.getString("type")

            when (type) {
                "TILE_DRAWN" -> {
                    val tileJson = json.getJSONObject("tile")
                    val tile = parseTileFromJson(tileJson)
                    onTileDrawn(tile)
                }

                "error" -> {
                    val message = json.getString("message")
                    Log.e("WebSocket", "Error from server: $message")

                    if (message.contains("no more playable tiles", ignoreCase = true)) {
                        clearCurrentTile()
                    }
                }

                else -> {
                    Log.d("WebSocket", "Unhandled message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e("WebSocket", "Failed to parse WebSocket message: ${e.message}")
        }
    }


    fun onTileDrawn(tile: Tile) {
        _currentTile.value = tile
        Log.d("WebSocket", "Tile drawn and set: $tile")
    }

    private fun parseTileFromJson(json: JSONObject): Tile {
        return Tile(
            id = json.getString("id"),
            top = json.getString("terrainNorth"),
            right = json.getString("terrainEast"),
            bottom = json.getString("terrainSouth"),
            left = json.getString("terrainWest"),
            hasMonastery = json.optBoolean("hasMonastery", false),
            hasShield = json.optBoolean("hasShield", false),
            tileRotation = TileRotation.valueOf(json.optString("tileRotation", "NORTH"))
        )
    }

    fun tileCountFor(id: String): Int = when (id) {
        "tile-a" -> 2
        "tile-b" -> 4
        "tile-c" -> 1
        "tile-d" -> 4
        "tile-e" -> 5
        "tile-f" -> 2
        "tile-g" -> 1
        "tile-h" -> 3
        "tile-i" -> 2
        "tile-j" -> 3
        "tile-k" -> 3
        "tile-m" -> 3
        "tile-n" -> 3
        "tile-o" -> 2
        "tile-p" -> 2
        "tile-q" -> 3
        "tile-r" -> 1
        "tile-s" -> 3
        "tile-t" -> 2
        "tile-u" -> 1
        "tile-v" -> 8
        "tile-w" -> 9
        "tile-x" -> 4
        "tile-y" -> 1
        else -> 1
    }

    fun createShuffledDeck() {
        val baseTiles = listOf(
            Tile("tile-a", "FIELD", "FIELD", "ROAD", "FIELD", hasMonastery = true),
            Tile("tile-b", "FIELD", "FIELD", "FIELD", "FIELD", hasMonastery = true),
            Tile("tile-c", "CITY", "CITY", "CITY", "CITY", hasShield = true),
            Tile("tile-d", "CITY", "ROAD", "FIELD", "ROAD"),
            Tile("tile-e", "CITY", "FIELD", "FIELD", "FIELD", hasShield = true),
            Tile("tile-f", "FIELD", "CITY", "FIELD", "CITY", hasShield = true),
            Tile("tile-g", "FIELD", "CITY", "FIELD", "CITY"),
            Tile("tile-h", "CITY", "FIELD", "CITY", "FIELD"),
            Tile("tile-i", "CITY", "FIELD", "FIELD", "CITY"),
            Tile("tile-j", "CITY", "ROAD", "ROAD", "FIELD"),
            Tile("tile-k", "CITY", "FIELD", "ROAD", "ROAD"),
            Tile("tile-m", "CITY", "ROAD", "ROAD", "ROAD"),
            Tile("tile-n", "CITY", "CITY", "FIELD", "FIELD"),
            Tile("tile-o", "CITY", "CITY", "FIELD", "FIELD", hasShield = true),
            Tile("tile-p", "CITY", "ROAD", "ROAD", "CITY", hasShield = true),
            Tile("tile-q", "CITY", "ROAD", "ROAD", "CITY"),
            Tile("tile-r", "CITY", "CITY", "FIELD", "CITY", hasShield = true),
            Tile("tile-s", "CITY", "CITY", "FIELD", "CITY"),
            Tile("tile-t", "CITY", "CITY", "ROAD", "CITY", hasShield = true),
            Tile("tile-u", "CITY", "CITY", "ROAD", "CITY"),
            Tile("tile-v", "ROAD", "FIELD", "ROAD", "FIELD"),
            Tile("tile-w", "FIELD", "FIELD", "ROAD", "ROAD"),
            Tile("tile-x", "FIELD", "ROAD", "ROAD", "ROAD"),
            Tile("tile-y", "ROAD", "ROAD", "ROAD", "ROAD")
        )

        val fullDeck = baseTiles.flatMap { tile ->
            List(tileCountFor(tile.id)) { index -> tile.copy(id = "${tile.id}-$index") }
        }

        _tileDeck.clear()
        _tileDeck.addAll(fullDeck.shuffled())
    }

    fun drawNextTile() {
        _currentTile.value = if (_tileDeck.isNotEmpty()) _tileDeck.removeAt(0) else null
        println("Drawn tile: ${_currentTile.value}")
    }

    fun Tile.rotateClockwise(): Tile {
        return this.copy(
            top = left,
            right = top,
            bottom = right,
            left = bottom,
            tileRotation = when (tileRotation) {
                TileRotation.NORTH -> TileRotation.EAST
                TileRotation.EAST -> TileRotation.SOUTH
                TileRotation.SOUTH -> TileRotation.WEST
                TileRotation.WEST -> TileRotation.NORTH
            }
        )
    }

    fun rotateCurrentTile() {
        _currentTile.value = _currentTile.value?.rotateClockwise()
    }
    fun clearCurrentTile() {
        _currentTile.value = null
        Log.d("WebSocket", "Current tile cleared due to no playable tiles")
    }

}
