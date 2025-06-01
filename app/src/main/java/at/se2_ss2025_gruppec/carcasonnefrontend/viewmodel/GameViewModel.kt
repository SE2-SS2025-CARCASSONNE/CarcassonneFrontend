package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GameState
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Meeple
import at.se2_ss2025_gruppec.carcasonnefrontend.model.MeeplePosition
import at.se2_ss2025_gruppec.carcasonnefrontend.model.MeepleType
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.repository.GameRepository
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.Callbacks
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class GameViewModel constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _currentTile = mutableStateOf<Tile?>(null)
    val currentTile: State<Tile?> get() = _currentTile

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState

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

    private fun handleWebSocketMessage(msg: String) {
        try {
            val json = JSONObject(msg)
            val type = json.getString("type")

            when (type) {
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

                "meeple_placed" -> {
                    try {
                        // Extrahiere die Meeple-Informationen aus der Nachricht
                        val meepleJson = json.getJSONObject("meeple")
                        val meeple = parseMeepleFromJson(meepleJson)

                        // Extrahiere die Spieler-ID, die das Meeple gesetzt hat
                        val playerId = json.getString("player")

                        // Extrahiere die Position des Meeples
                        val position = Position(
                            x = meepleJson.getInt("x"),
                            y = meepleJson.getInt("y")
                        )

                        // Aktualisiere das Spielfeld mit dem gesetzten Meeple
                        updateBoardWithMeeple(meeple, position, playerId)

                        Log.d("WebSocket", "Meeple gesetzt: ${meeple.id} an Position ($position.x, $position.y)")

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

    fun placeMeeple(gameId: String, playerId: String, meepleId: String, tileId: String, position: String) {
        webSocketClient.sendPlaceMeeple(gameId, playerId, meepleId, tileId, position)
        Log.d("GameViewModel", "Meeple placement requested: $meepleId by player $playerId")
    }

    private fun parseMeepleFromJson(json: JSONObject): Meeple {
        return Meeple(
            id = json.getString("id"),
            playerId = json.getString("playerId"),
            tileId = json.getString("tileId"),
            position = MeeplePosition.valueOf(json.getString("position")),
            type = MeepleType.valueOf(json.getString("type"))
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