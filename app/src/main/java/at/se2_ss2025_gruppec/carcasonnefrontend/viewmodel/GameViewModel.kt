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

class GameViewModel : ViewModel() {

    private var joinedPlayerName: String? = null
    fun setJoinedPlayer(name: String) { joinedPlayerName = name }

    private val _placedTiles = mutableStateListOf<Tile>()

    private val _currentTile = mutableStateOf<Tile?>(null)
    val currentTile: State<Tile?> get() = _currentTile

    private val _deckRemaining = MutableStateFlow(0)
    val deckRemaining: StateFlow<Int> = _deckRemaining

    private val _canExpose = MutableStateFlow(true)
    val canExpose: StateFlow<Boolean> get() = _canExpose

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

    fun requestTileFromBackend(gameId: String, playerId: String) {
        if (webSocketClient.isConnected()) {
            webSocketClient.sendDrawTileRequest(gameId, playerId)
        } else {
            Log.e("WebSocket", "Not connected yet, can't draw tile.")
        }
    }

    fun cheatRedraw(gameId: String) {
        joinedPlayerName?.let { webSocketClient.sendCheatRedraw(gameId, it) }
    }

    fun exposeCheater(gameId: String) {
        if (_canExpose.value) {
            webSocketClient.sendExposeCheater(gameId, joinedPlayerName!!)
            _canExpose.value = false
        }
    }

    fun handleWebSocketMessage(msg: String) {
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
                }

                "CHEAT_TILE_DRAWN" -> {
                    val tileJson = json.getJSONObject("tile")
                    val tile = parseTileFromJson(tileJson)
                    onTileDrawn(tile)

                    viewModelScope.launch {
                        _errorEvents.send("You cheated...")
                    }
                }

                "expose_success" -> {
                    val culprit = json.getString("culprit")
                    val accuser = json.getString("accuser")
                    _canExpose.value = false
                    updateGameWithScore(json)

                    viewModelScope.launch {
                        _errorEvents.send("$accuser exposed $culprit! –2 points")
                    }
                }

                "expose_fail" -> {
                    val accuser = json.getString("player")
                    if (accuser == joinedPlayerName) {
                        viewModelScope.launch {
                            _errorEvents.send("False accusation! –1 point")
                        }
                    }
                    updateGameWithScore(json)
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

                    _isMeeplePlacementActive.value = false
                    _currentTile.value = null
                    _currentPlayerId.value = json.getString("nextPlayer")
                    _canExpose.value = true

                    val current = _uiState.value
                    if (current is GameUiState.Success) {
                        _uiState.value = GameUiState.Success(
                            current.gameState.copy(
                                gamePhase = newPhase)
                        )
                    }

                    val scoresArr = json.getJSONArray("scores")
                    for (i in 0 until scoresArr.length()) {
                        val entry = scoresArr.getJSONObject(i)
                        val playerId = entry.getString("player")
                        val remaining = entry.getInt("remainingMeeple")
                        updateRemainingMeeples(playerId, remaining)
                    }
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

                "meeple_removed" -> {
                    val ids = json.getJSONArray("ids").let { arr ->
                            List(arr.length()) { i -> arr.getString(i) }
                        }

                    (_uiState.value as? GameUiState.Success)?.let { success ->
                        val meeplesOnBoard = success.gameState.meeples
                            .filterNot { it.id in ids }
                        _uiState.value = GameUiState.Success(
                            success.gameState.copy(meeples = meeplesOnBoard)
                        )
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
            center = json.getString("terrainCenter"),
            hasMonastery = json.optBoolean("hasMonastery", false),
            hasShield = json.optBoolean("hasShield", false),
            tileRotation = TileRotation.valueOf(json.optString("tileRotation", "NORTH"))
        )
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

        // Clear drawn tile
        _currentTile.value  = null

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
                put("terrainCenter", tile.center)
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