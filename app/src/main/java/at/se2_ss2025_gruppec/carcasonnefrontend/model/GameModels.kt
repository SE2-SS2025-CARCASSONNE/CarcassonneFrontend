package at.se2_ss2025_gruppec.carcasonnefrontend.model

import androidx.compose.ui.graphics.Color

data class Player(
    val id: String,
    val name: String,
    val color: Color,
    val score: Int = 0,
    val availableMeeples: Int = 7
)

enum class TerrainType {
    ROAD,
    CITY,
    MONASTERY,
    FIELD,
}

enum class TileRotation(val degrees: Int) {
    NORTH(0),
    EAST(90),
    SOUTH(180),
    WEST(270)
}

data class Position(val x: Int, val y: Int)

data class Tile(
    val id: String,
    val top: String,
    val right: String,
    val bottom: String,
    val left: String,
    val hasMonastery: Boolean = false,
    val hasShield: Boolean = false,
    val tileRotation: TileRotation = TileRotation.NORTH,
    val position: Position? = null,
    val drawableRes: Int? = null
)

enum class MeepleType {
    KNIGHT,
    THIEF,
    MONK
}

enum class MeeplePosition {
    NORTH,
    EAST,
    SOUTH,
    WEST,
    CENTER
}

data class Meeple(
    val id: String,
    val playerId: String,
    val type: MeepleType,
    val tileId: String? = null,
    val position: MeeplePosition? = null
)

data class GameState(
    val id: String,
    val players: List<Player>,
    val currentPlayerIndex: Int,
    val board: Map<Position, Tile>,
    val remainingTiles: List<Tile>,
    val currentTile: Tile?,
    val meeples: List<Meeple>,
    val gamePhase: GamePhase
)

enum class GamePhase {
    WAITING,
    TILE_PLACEMENT,
    MEEPLE_PLACEMENT,
    SCORING,
    GAME_OVER
}