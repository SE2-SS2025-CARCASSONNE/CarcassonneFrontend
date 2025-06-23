package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Position
import at.se2_ss2025_gruppec.carcasonnefrontend.model.Tile
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TileRotation
import at.se2_ss2025_gruppec.carcasonnefrontend.websocket.MyClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: GameViewModel
    private lateinit var mockClient: MyClient

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockClient = mockk(relaxed = true)
        viewModel = GameViewModel()
        viewModel.setWebSocketClient(mockClient)
        viewModel.setJoinedPlayer("player1")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `rotateCurrentTile should rotate clockwise`() = runTest {
        val tile = Tile(
            "t1", "ROAD", "CITY", "FIELD", "CITY", tileRotation = TileRotation.NORTH,
            center = "FIELD",
            hasMonastery = false,
            hasShield = false
        )
        viewModel.onTileDrawn(tile)

        viewModel.rotateCurrentTile()
        assertEquals(TileRotation.EAST, viewModel.currentTile.value?.tileRotation)

        viewModel.rotateCurrentTile()
        assertEquals(TileRotation.SOUTH, viewModel.currentTile.value?.tileRotation)
    }

    @Test
    fun `clearCurrentTile should reset currentTile to null`() = runTest {
        val tile = Tile(
            "t1", "CITY", "CITY", "ROAD", "FIELD",
            center = "FIELD",
            hasMonastery = false,
            hasShield = false
        )
        viewModel.onTileDrawn(tile)
        viewModel.clearCurrentTile()

        assertEquals(null, viewModel.currentTile.value)
    }

    @Test
    fun `placeTileAt should send tile payload if connected`() = runTest {
        every { mockClient.isConnected() } returns true

        val tile =
            Tile(
                "tile123", "ROAD", "CITY", "FIELD", "ROAD", tileRotation = TileRotation.NORTH,
                center = "FIELD",
                hasMonastery = false,
                hasShield = false
            )
        viewModel.onTileDrawn(tile)

        val pos = Position(2, 3)
        viewModel.placeTileAt(pos, "game123")

        verify {
            mockClient.sendPlaceTileRequest(match {
                it.contains("\"type\":\"place_tile\"") &&
                        it.contains("\"gameId\":\"game123\"") &&
                        it.contains("\"x\":2") &&
                        it.contains("\"y\":3") &&
                        it.contains("\"id\":\"tile123\"")
            })
        }
    }

    @Test
    fun `placeTileAt should not send if WebSocket is not connected`() = runTest {
        every { mockClient.isConnected() } returns false
        val tile = Tile("tile123", "CITY", "FIELD", "ROAD", "ROAD",
            center = "FIELD",
            hasMonastery = false,
            hasShield = false
        )
        viewModel.onTileDrawn(tile)
        viewModel.placeTileAt(Position(1, 1), "gameABC")

        verify(exactly = 0) { mockClient.sendPlaceTileRequest(any()) }
    }

    @Test
    fun `placeMeeple should forward correct parameters`() = runTest {
        viewModel.placeMeeple("game1", "p1", "m1", "tile1", "CENTER")

        verify {
            mockClient.sendPlaceMeeple("game1", "p1", "m1", "tile1", "CENTER")
        }
    }

    @Test
    fun `skipMeeple should call sendSkipMeeple if joined player set`() = runTest {
        viewModel.skipMeeple("gameX")
        verify {
            mockClient.sendSkipMeeple("gameX", "player1")
        }
    }

    @Test
    fun `setMeeplePlacement and updateRemainingMeeples should update state flows`() = runTest {
        viewModel.setMeeplePlacement(true)
        assertTrue(viewModel.isMeeplePlacementActive.value)

        viewModel.updateRemainingMeeples("p1", 5)
        assertEquals(5, viewModel.remainingMeeples.value["p1"])
    }

    @Test
    fun `isValidPlacement returns false if tile is null or already placed`() = runTest {
        assertFalse(viewModel.isValidPlacement(Position(0, 0)))

        val tile = Tile("t1", "FIELD", "FIELD", "ROAD", "FIELD", position = Position(1, 1),
            center = "FIELD",
            hasMonastery = false,
            hasShield = false
        )
        viewModel.onTileDrawn(tile)
        viewModel.placeTileAt(Position(1, 1), "gameZ") // will place it

        // Simulate board update manually
        viewModel.handleWebSocketMessage(
            """{
                "type":"board_update",
                "tile":{
                    "id":"t1",
                    "terrainNorth":"FIELD",
                    "terrainEast":"FIELD",
                    "terrainSouth":"ROAD",
                    "terrainWest":"FIELD",
                    "tileRotation":"NORTH",
                    "position":{"x":1,"y":1},
                },
                "player":{"id":"player1"},
                "gamePhase":"TILE_PLACEMENT"
            }"""
        )
        advanceUntilIdle()

        assertFalse(viewModel.isValidPlacement(Position(1, 1))) // Already occupied
    }
}
