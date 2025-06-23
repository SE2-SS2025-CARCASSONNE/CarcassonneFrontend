package at.se2_ss2025_gruppec.carcasonnefrontend.model

// Updated to match backend Player model
data class PlayerDTO(
    val id: String,
    val score: Int,
    val meeplesLeft: Int,
    val pointsThisTurn: Int
)

// Updated to use PlayerDTO instead of List<String>
data class GameStateDTO(
    val gameId: String,
    val players: List<PlayerDTO>,
    val status: String,
    val currentPlayerIndex: Int
)

data class UserStatsDto(
    val totalGames: Int,
    val totalWins: Int,
    val winRatio: Double,
    val highScore: Int
)

data class CreateGameRequest(val playerCount: Int, val hostName: String)
data class CreateGameResponse(val gameId: String)

data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val password: String)

data class TokenResponse(val token: String)

