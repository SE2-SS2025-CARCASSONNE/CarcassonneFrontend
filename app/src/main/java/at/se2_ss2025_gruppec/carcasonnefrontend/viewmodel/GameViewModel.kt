package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GameState
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Meeple
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.repository.GameRepository
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.Callbacks
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import com.carcassonne.model.dto.PlaceTileDto
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

    fun placeTile(position: Position) {
        val tile = _selectedTile.value ?: return
        val gameState = (_uiState.value as? GameUiState.Success)?.gameState ?: return
        val playerId = gameState.players[gameState.currentPlayerIndex].id

        viewModelScope.launch {
            try {
                val placeTileDto = PlaceTileDto(
                    gameState.id,
                    tile.id,
                    position.x,
                    position.y,
                    _currentRotation.value
                )
                val success = gameRepository.placeTile(
                    tile.id,
                    placeTileDto
                )
                if (success) {
                    _selectedTile.value = null // Clear after placement
                }
            } catch (e: Exception) {
                _uiState.value = GameUiState.Error("Error placing tile: ${e.message}")
            }
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