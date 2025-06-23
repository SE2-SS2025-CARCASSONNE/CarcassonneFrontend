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
    FIELD
}

data class ScoringEvent(
    val playerId: String,
    val points:   Int,
    val feature:  String
)

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
    val center: String,
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
    N, // North
    E, // East
    S, // South
    W, // West
    C  // Center (Monastery)
}

data class Meeple(
    val id: String,
    val playerId: String,
    // val type: MeepleType, ehemals, wird jetzt in Backend gelöst
    val tileId: String? = null,
    val position: MeeplePosition? = null, //N, E, S, W oder C
    val x: Int,                   // Board‐X
    val y: Int                    // Board‐Y
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