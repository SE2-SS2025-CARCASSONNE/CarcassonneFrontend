package at.se2_ss2025_gruppec.carcasonnefrontend.repository

import android.util.Log
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import com.carcassonne.model.dto.GameListItemDto
import com.carcassonne.model.dto.GameStateDto
import com.carcassonne.model.dto.PlaceTileDto
import com.carcassonne.network.CarcassonneApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GameRepository constructor(
    private val api: CarcassonneApi,
    private val webSocketClient: MyClient
) {
    private val TAG = "GameRepository"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _gameListFlow = MutableStateFlow<List<GameListItemDto>>(emptyList())
    val gameListFlow: StateFlow<List<GameListItemDto>> = _gameListFlow.asStateFlow()

    private val _gameStateFlow = MutableStateFlow<GameStateDto?>(null)
    val gameStateFlow: StateFlow<GameStateDto?> = _gameStateFlow.asStateFlow()

    init {
        // WebSocket-Nachrichten beobachten
        scope.launch {
            // TODO: Observe WebSocket messages continuously

        }
    }

    private suspend fun refreshGameState(gameId: String) {
        try {
            val response = withContext(Dispatchers.IO) {
                api.getGameState(gameId)
            }

            // Create response that returns the new game state
            if (response.isSuccessful) {
                response.body()?.let { gameState ->
                    _gameStateFlow.value = gameState
                }
            } else {
                Log.e(TAG, "Error loading game state: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading game state", e)
        }
    }

    suspend fun placeTile(gameId: String, placeTileDto: PlaceTileDto): Boolean {
        try {
            val response = withContext(Dispatchers.IO) {
                // API call to placeTile
                api.placeTile(gameId, placeTileDto)
            }

            if (response.isSuccessful && response.body() == true) {
                // refresh GameState
                refreshGameState(gameId)
                return true
            } else {
                Log.e(TAG, "Error placing tile: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error placing tile", e)
        }
        return false
    }
}