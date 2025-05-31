package at.se2_ss2025_gruppec.carcasonnefrontend.model.dto

import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation

// DTOs für die Kommunikation zwischen Frontend und Backend

// Benutzer-DTO
data class UserDto(
    val id: String,
    val username: String,
    val email: String
)

// Authentifizierungs-Request-DTO
data class AuthRequestDto(
    val username: String,
    val password: String
)

// Authentifizierungs-Response-DTO
data class AuthResponseDto(
    val token: String,
    val user: UserDto
)

// Spieler-DTO
data class PlayerDto(
    val id: String,
    val name: String,
    val color: String,
    val score: Int,
    val availableMeeples: Int
)

// Position-DTO
data class PositionDto(
    val x: Int,
    val y: Int
)

// Plättchen-DTO
data class TileDto(
    val id: String,
    val terrainNorth: String,
    val terrainEast: String,
    val terrainSouth: String,
    val terrainWest: String,
    val hasMonastery: Boolean,
    val hasShield: Boolean,
    val rotation: Int,
    val position: PositionDto?
)

// Meeple-DTO
data class MeepleDto(
    val id: String,
    val playerId: String,
    val type: String,
    val tileId: String?,
    val position: String?
)

// Spielzustand-DTO
data class GameStateDto(
    val id: String,
    val players: List<PlayerDto>,
    val currentPlayerIndex: Int,
    val board: Map<String, TileDto>,
    val remainingTilesCount: Int,
    val currentTile: TileDto?,
    val meeples: List<MeepleDto>,
    val gamePhase: String
)

// Spiel-Erstellungs-DTO
data class CreateGameDto(
    val name: String,
    val maxPlayers: Int,
    val includeRiverExpansion: Boolean = false,
    val includeAbbotExpansion: Boolean = false
)

// Spiel-Beitritts-DTO
data class JoinGameDto(
    val gameId: String,
    val playerName: String,
    val preferredColor: String?
)

// Plättchen-Platzierungs-DTO
data class PlaceTileDto(
    val gameId: String,
    val tileId: String,
    val x: Int,
    val y: Int,
    val rotation: TileRotation
)

// Meeple-Platzierungs-DTO
data class PlaceMeepleDto(
    val gameId: String,
    val meepleId: String,
    val tileId: String,
    val position: String
)

// Spiel-Liste-DTO
data class GameListItemDto(
    val id: String,
    val name: String,
    val creatorName: String,
    val playerCount: Int,
    val maxPlayers: Int,
    val gamePhase: String,
    val createdAt: String
)

// Spielergebnis-DTO
data class GameResultDto(
    val gameId: String,
    val players: List<PlayerDto>,
    val winnerId: String,
    val scores: Map<String, Int>
)

// WebSocket-Nachricht-DTO
data class WebSocketMessageDto(
    val type: String,
    val gameId: String,
    val payload: Any
)

// Chat-Nachricht-DTO
data class ChatMessageDto(
    val gameId: String,
    val playerId: String,
    val playerName: String,
    val message: String,
    val timestamp: Long
)
