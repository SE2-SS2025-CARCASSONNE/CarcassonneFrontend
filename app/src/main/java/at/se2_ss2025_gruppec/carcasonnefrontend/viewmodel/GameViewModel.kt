package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GamePhase
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GameState
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Meeple
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Player
import at.se2_ss2025_gruppec.carcasonnefrontend.model.MeeplePosition
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

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

    private var joinedPlayerName: String? = null
    fun setJoinedPlayer(name: String) { joinedPlayerName = name }

    private val _tileDeck = mutableStateListOf<Tile>()
    val tileDeck: List<Tile> = _tileDeck

    private val _placedTiles = mutableStateListOf<Tile>()
    val placedTiles: List<Tile> = _placedTiles

    private val _currentTile = mutableStateOf<Tile?>(null)
    val currentTile: State<Tile?> get() = _currentTile

    private val _validPlacements = MutableStateFlow<List<Pair<Position,TileRotation>>>(emptyList())
    val validPlacements: StateFlow<List<Pair<Position,TileRotation>>> = _validPlacements

    private val _deckRemaining = MutableStateFlow(0)
    val deckRemaining: StateFlow<Int> = _deckRemaining

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState

    private val _players = mutableStateListOf<Player>()
    val players: List<Player> get() = _players

    private val _currentPlayerId = mutableStateOf<String?>(null)
    val currentPlayerId: State<String?> = _currentPlayerId

    private val _isMeeplePlacementActive = MutableStateFlow(false)
    val isMeeplePlacementActive: StateFlow<Boolean> get() = _isMeeplePlacementActive

    private val _remainingMeeples = MutableStateFlow(mapOf<String, Int>())
    val remainingMeeples: StateFlow<Map<String, Int>> get() = _remainingMeeples

    // 1A) Channel für Error-Events vom WebSocket
    private val _errorEvents = Channel<String>(Channel.BUFFERED)
    val errorEvents = _errorEvents.receiveAsFlow()


    fun setMeeplePlacement(active: Boolean) {
        _isMeeplePlacementActive.value = active
        Log.d("MeeplePlacement", "Meeple Placement Active: ${_isMeeplePlacementActive.value}") //TODO Mike dann wieder entfernen!
    }

    fun updateRemainingMeeples(playerId: String, meepleCount: Int) {
        _remainingMeeples.value = _remainingMeeples.value.toMutableMap().apply {
            this[playerId] = meepleCount
        }
    }

    private lateinit var webSocketClient: MyClient

    fun setWebSocketClient(client: MyClient) {
        webSocketClient = client
    }

    fun joinGame(gameId: String, playerName: String) {
        webSocketClient.sendJoinGame(gameId, playerName)
    }

    fun subscribeToGame(gameId: String) {
        webSocketClient.listenOn("/topic/game/$gameId") { msg ->
            handleWebSocketMessage(msg)
        }
    }

    fun subscribeToPrivate() {
        webSocketClient.listenOn("/user/queue/private") { msg ->
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
                "player_joined" -> {
                    val arr = json.getJSONArray("players")

                    val colors = listOf(Color.Blue, Color.Yellow, Color.Red, Color.Green)

                    val newPlayers = (0 until arr.length()).map { i ->
                        Player(
                            id = arr.getString(i),
                            name = arr.getString(i),
                            score = 0,
                            availableMeeples = 7,
                            color = colors.getOrElse(i) { Color.Gray }
                        )
                    }
                    _players.clear()
                    _players.addAll(newPlayers)
                    _currentPlayerId.value = json.getString("currentPlayer")
                }

                "TILE_DRAWN" -> {
                    val tileJson = json.getJSONObject("tile")
                    val tile = parseTileFromJson(tileJson)
                    onTileDrawn(tile)

                    if (json.has("validPlacements")) {
                        val validPlacementsJson = json.getJSONArray("validPlacements")
                        val validPlacementList = mutableListOf<Pair<Position,TileRotation>>()
                        for (i in 0 until validPlacementsJson.length()) {
                            val temp = validPlacementsJson.getJSONObject(i)
                            val posObj = temp.getJSONObject("position")
                            val position = Position(posObj.getInt("x"), posObj.getInt("y"))
                            val rotation = TileRotation.valueOf(temp.getString("rotation"))
                            validPlacementList += position to rotation
                        }
                        _validPlacements.value = validPlacementList
                    }
                }

                "deck_update" -> {
                    _deckRemaining.value = json.getInt("deckRemaining")
                }

                "board_update" -> {
                    Log.d("WebSocket", "Received board update: $msg")
                    try {
                        // Extract tile information from the payload
                        val tileJson = json.getJSONObject("tile")
                        val placedTile = parseTileFromJson(tileJson)

                        // Extract player information if needed
                        val playerJson = json.optJSONObject("player")
                        val playerId = playerJson?.getString("id")

                        // Extract position information
                        val positionJson = tileJson.getJSONObject("position")

                        val position = Position(
                            x = positionJson.getInt("x"),
                            y = positionJson.getInt("y")
                        )

                        // Create updated tile with position
                        val tileWithPosition = placedTile.copy(position = position)

                        // Update the board state
                        val phaseFromServer = json.optString("gamePhase", GamePhase.TILE_PLACEMENT.name)
                        val gamePhase = GamePhase.valueOf(phaseFromServer)
                        updateBoardWithTile(tileWithPosition, playerId, gamePhase)

                        setMeeplePlacement(gamePhase == GamePhase.MEEPLE_PLACEMENT)

                        Log.d(
                            "WebSocket",
                            "Board updated with tile: ${placedTile.id} at position ($position.x, $position.y)"
                        )

                    } catch (e: Exception) {
                        Log.e("WebSocket", "Failed to process board_update: ${e.message}")
                        _uiState.value = GameUiState.Error("Failed to update board: ${e.message}")
                    }
                }

                "score_update" -> {
                    updateGameWithScore(json)

                    val phaseStr = json.optString("gamePhase", GamePhase.TILE_PLACEMENT.name)
                    val newPhase = GamePhase.valueOf(phaseStr)
                    val current = _uiState.value
                    if (current is GameUiState.Success) {
                        _uiState.value = GameUiState.Success(
                            current.gameState.copy(gamePhase = newPhase)
                        )
                    }
                    _isMeeplePlacementActive.value = false
                    _currentTile.value = null
                    _validPlacements.value = emptyList()
                    _currentPlayerId.value = json.getString("nextPlayer")
                }

                "error" -> {
                    val message = json.getString("message")
                    Log.e("WebSocket", "Error from server: $message")

                    // Fehlermeldung in den Channel senden
                    viewModelScope.launch {
                        _errorEvents.send(message)
                    }

                    if (message.contains("no more playable tiles", ignoreCase = true)) {
                        clearCurrentTile()
                    }
                }

                "meeple_placed" -> {
                    try {
                        // Extrahiere die Meeple-Informationen aus der Nachricht
                        val meepleJson = json.getJSONObject("meeple")
                        val meeple = parseMeepleFromJson(meepleJson)
                        val remainingMeeple = json.getInt("remainingMeeple")

                        // Extrahiere die Spieler-ID, die das Meeple gesetzt hat
                        val playerId = json.getString("player")

                        // Extrahiere die Position des Meeples
                        val position = Position(
                            x = meepleJson.getInt("x"),
                            y = meepleJson.getInt("y")
                        )

                        // Aktualisiere das Spielfeld mit dem gesetzten Meeple
                        updateBoardWithMeeple(meeple, position, playerId)

                        // Meeple-Anzahl aktualisieren
                        updateRemainingMeeples(playerId, remainingMeeple)

                        val phaseStr   = json.optString(
                            "gamePhase",
                            GamePhase.TILE_PLACEMENT.name        // Fallback, falls Server (noch) nichts sendet
                        )
                        val newPhase   = GamePhase.valueOf(phaseStr)

                        // Jetzt das Tile endgültig aus der BottomBar entfernen:
                        clearCurrentTile()

                        // Meeple-Modus aktivieren, wenn wir uns in der Phase MEEPLE_PLACEMENT befinden
                        setMeeplePlacement(newPhase == GamePhase.MEEPLE_PLACEMENT)

                        // Log.d("WebSocket", "Meeple gesetzt: ${meeple.id} an Position ($position.x, $position.y)")
                        Log.d("GameViewModel", "Meeple gesetzt: ${meeple.id}, verbleibende Meeples für $playerId: $remainingMeeple")

                    } catch (e: Exception) {
                        Log.e("WebSocket", "Fehler beim Verarbeiten von meeple_placed: ${e.message}")
                        _uiState.value = GameUiState.Error("Fehler beim Setzen des Meeples: ${e.message}")
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

    private fun updateBoardWithTile(tile: Tile, playerId: String?, phase: GamePhase) {
        val position = tile.position ?: return
        Log.d("GameViewModel", "updateBoardWithTile: placing ${tile.id} at $position")

        // Get old board or initialize new one & place tile
        val currentState = _uiState.value
        val updatedBoard = when (currentState) {
            is GameUiState.Success -> currentState.gameState.board.toMutableMap()
            else -> mutableMapOf()
        }
        updatedBoard[position] = tile

        // Create new game state with updated board
        val updatedGameState = when (currentState) {
            is GameUiState.Success -> currentState.gameState.copy(
                board = updatedBoard,
                currentTile = null, // Clear current tile after placement
                gamePhase = phase
            )
            else -> GameState(
                id = "unknown_game",
                players = listOf(Player(playerId ?: "host", playerId ?: "host", Color.White)),
                currentPlayerIndex = 0,
                board = updatedBoard,
                remainingTiles = emptyList(),
                currentTile = null,
                meeples = emptyList(),
                gamePhase = GamePhase.TILE_PLACEMENT
            )
        }

        // Emit updated UI state
        _uiState.value = GameUiState.Success(updatedGameState)

        // Sync placed tiles with updated board
        _placedTiles.clear()
        _placedTiles.addAll(updatedBoard.values)

        // Clear drawn tile and valid placements
        _currentTile.value  = null
        _validPlacements.value = emptyList()

        Log.d("GameViewModel", "Board now has ${updatedBoard.size} placed tiles")
    }

    fun placeTileAt(position: Position, gameId: String) {
        Log.d("ViewModel", "placeTileAt called with $position")
        val tile = _currentTile.value ?: return

        if (joinedPlayerName == null) {
            Log.e("placeTileAt", " Cannot place tile - player name is null")
            return
        }

        val playerId = joinedPlayerName ?: return

        Log.d("placeTileAt", "Placing tile at $position for playerId = $playerId")

        val payload = JSONObject().apply {
            put("type", "place_tile")
            put("gameId", gameId)
            put("player", playerId)
            put("tile", JSONObject().apply {
                put("id", tile.id)
                put("terrainNorth", tile.top)
                put("terrainEast", tile.right)
                put("terrainSouth", tile.bottom)
                put("terrainWest", tile.left)
                put("tileRotation", tile.tileRotation)
                put("hasMonastery", tile.hasMonastery)
                put("hasShield", tile.hasShield)
                put("position", JSONObject().apply {
                    put("x", position.x)
                    put("y", position.y)
                })
            })
        }

        if (webSocketClient.isConnected()) {
            webSocketClient.sendPlaceTileRequest(payload.toString())
            Log.d("WebSocket", "Tile placement request sent: $payload")
        } else {
            Log.e("WebSocket", "Cannot send tile - WebSocket not connected")
        }
    }

    fun isValidPlacement(position: Position): Boolean {
        if (_currentTile.value == null) return false

        // Disallow placing on already occupied position
        if (_placedTiles.any { it.position == position }) return false

        // Check if there's any adjacent tile
        val neighbors = listOf(
            Position(position.x, position.y - 1),
            Position(position.x + 1, position.y),
            Position(position.x, position.y + 1),
            Position(position.x - 1, position.y)
        )

        return neighbors.any { neighbor ->
            _placedTiles.any { it.position == neighbor }
        }
    }

    fun placeMeeple(gameId: String, playerId: String, meepleId: String, tileId: String, position: String) {
        webSocketClient.sendPlaceMeeple(gameId, playerId, meepleId, tileId, position)
        Log.d("GameViewModel", "Meeple placement requested: $meepleId by player $playerId")
    }

    fun skipMeeple(gameId: String) {
        _isMeeplePlacementActive.value = false
        joinedPlayerName?.let { webSocketClient.sendSkipMeeple(gameId, it) }
    }

    private fun parseMeepleFromJson(json: JSONObject): Meeple {
        return Meeple(
            id = json.getString("id"),
            playerId = json.getString("playerId"),
            tileId = json.getString("tileId"),
            position = MeeplePosition.valueOf(json.getString("position")),
            x        = json.getInt("x"),
            y        = json.getInt("y")
            //type = MeepleType.valueOf(json.getString("type"))
        )
    }

    private fun updateBoardWithMeeple(meeple: Meeple, position: Position, playerId: String) {
        val currentState = _uiState.value
        if (currentState is GameUiState.Success) {
            val updatedMeeples = currentState.gameState.meeples.toMutableList()
            updatedMeeples.add(meeple)

            val updatedGameState = currentState.gameState.copy(
                meeples = updatedMeeples
            )

            _uiState.value = GameUiState.Success(updatedGameState)
            Log.d("GameViewModel", "Meeple placed successfully: ${meeple.id} by player $playerId")
        } else {
            Log.e("GameViewModel", "Cannot place meeple - Game state is not in Success")
        }
    }

    fun setCurrentPlayerId(id: String) {
        _currentPlayerId.value = id
    }

    fun updateGameWithScore(json: JSONObject) {
        // Get array of player scores and build map by player ID
        val arr = json.getJSONArray("scores")
        val byId = (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            o.getString("player") to o
        }

        // Update score and remainingMeeple fields on Player list
        _players.replaceAll { existing ->
            byId[existing.id]?.let { payload ->
                existing.copy(
                    score = payload.getInt("score"),
                    availableMeeples = payload.optInt("remainingMeeple", existing.availableMeeples)
                )
            } ?: existing
        }

        // Advance the UI to new current player
        setCurrentPlayerId(json.optString("nextPlayer"))
    }
}

/**
 * UI State to handle frontend screen behavior
 */
sealed class GameUiState {
    object Loading : GameUiState()
    data class Success(val gameState: GameState) : GameUiState()
    data class Error(val message: String) : GameUiState()
}