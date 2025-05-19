package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GameState
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Meeple
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.repository.GameRepository
import com.carcassonne.model.dto.PlaceTileDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GameViewModel constructor(
    private val gameRepository: GameRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<GameUiState>(GameUiState.Loading)
    val uiState: StateFlow<GameUiState> = _uiState

    private val _selectedTile = MutableStateFlow<Tile?>(null)
    val selectedTile: StateFlow<Tile?> = _selectedTile

    private val _currentRotation = MutableStateFlow(TileRotation.NORTH)
    val currentRotation: StateFlow<TileRotation> = _currentRotation

    private val _selectedMeeple = MutableStateFlow<Meeple?>(null)
    val selectedMeeple: StateFlow<Meeple?> = _selectedMeeple


    init {
        viewModelScope.launch {
            // TODO: Observe the backend GameState continuously
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