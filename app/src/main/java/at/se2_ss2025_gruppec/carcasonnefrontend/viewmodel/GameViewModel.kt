package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel


import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GameState
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Meeple
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Player
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.Callbacks
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

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

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState

    private val _players = mutableStateListOf<Player>()
    val players: List<Player> get() = _players

    private val _selectedTile = MutableStateFlow<Tile?>(null)
    val selectedTile: StateFlow<Tile?> = _selectedTile

    private val _currentRotation = MutableStateFlow(TileRotation.NORTH)
    val currentRotation: StateFlow<TileRotation> = _currentRotation

    private val _selectedMeeple = MutableStateFlow<Meeple?>(null)
    val selectedMeeple: StateFlow<Meeple?> = _selectedMeeple

    private lateinit var webSocketClient: MyClient

    init {
        viewModelScope.launch {
            webSocketClient = MyClient(object : Callbacks {
                override fun onResponse(msg: String) {
                    handleWebSocketMessage(msg)
                }

            })
            webSocketClient.connect()
        }
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
                "SCORE_UPDATED" -> {
                    handleScoreUpdateMessage(json)
                }
                "board_update" -> {
                    try {
                        // Extract tile information from the payload
                        val tileJson = json.getJSONObject("tile")
                        val placedTile = parseTileFromJson(tileJson)

                        // Extract player information if needed
                        val playerJson = json.optJSONObject("player")
                        val playerId = playerJson?.getString("id")

                        // Extract position information
                        val position = Position(
                            x = tileJson.getInt("x"),
                            y = tileJson.getInt("y")
                        )

                        // Create updated tile with position
                        val tileWithPosition = placedTile.copy(position = position)

                        // Update the board state
                        updateBoardWithTile(tileWithPosition, playerId)

                        Log.d("WebSocket", "Board updated with tile: ${placedTile.id} at position ($position.x, $position.y)")

                    } catch (e: Exception) {
                        Log.e("WebSocket", "Failed to process board_update: ${e.message}")
                        _uiState.value = GameUiState.Error("Failed to update board: ${e.message}")
                    }
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
    fun requestScoreUpdate(gameId: String) {
        if (webSocketClient.isConnected()) {
            webSocketClient.sendCalculateScoreRequest(gameId)
            Log.d("WebSocket", "Score update requested.")
        } else {
            Log.e("WebSocket", "WebSocket not connected!")
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

    private fun updateBoardWithTile(tile: Tile, playerId: String?) {
        val currentState = _uiState.value
        if (currentState is GameUiState.Success) {
            val position = tile.position ?: return

            // Update board map with the new tile
            val updatedBoard = currentState.gameState.board.toMutableMap()
            updatedBoard[position] = tile

            // Create new game state with updated board
            val updatedGameState = currentState.gameState.copy(
                board = updatedBoard,
                currentTile = null // Clear current tile after placement
            )

            // Emit updated UI state
            _uiState.value = GameUiState.Success(updatedGameState)

            // Clear selections
            _selectedTile.value = null
            _currentTile.value = null

            Log.d("GameViewModel", "Board updated with tile ${tile.id} at $position")
        } else {
            Log.e("GameViewModel", "Cannot update board - Game state is not in Success")
        }
    }


    fun selectTile(tile: Tile) {
        _selectedTile.value = tile
        _currentRotation.value = TileRotation.NORTH
    }

    private fun handleScoreUpdateMessage(json: JSONObject) {
        val scoreArray = json.getJSONArray("scores")
        val updatedPlayers = mutableListOf<Player>()
        for (i in 0 until scoreArray.length()) {
            val obj = scoreArray.getJSONObject(i)
            val player = Player(
                id = obj.getString("id"),
                name = "", //Dummy Werte bis zu Refactorn
                color = Color.Black,   //TODO Refactoren
                score = obj.getInt("score"),
                availableMeeples = obj.optInt("remainingMeeple", 0),
               // userId = if (obj.has("user_id")) obj.getInt("user_id") else null
            )
            updatedPlayers.add(player)
        }

        _players.clear()
        _players.addAll(updatedPlayers)
        Log.d("WebSocket", "Updated player scores: $_players")
    }

}

/**
 * UI State to handle frontend screen behavior
 */
sealed class GameUiState {
    object Loading : GameUiState()
    object Idle : GameUiState()
    data class Success(val gameState: GameState) : GameUiState()
    data class Error(val message: String) : GameUiState()
}