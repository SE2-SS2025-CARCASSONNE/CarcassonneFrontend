package at.se2_ss2025_gruppec.carcasonnefrontend.viewmodel
/*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GameViewModelTest {

    private lateinit var viewModel: GameViewModel

    @Before
    fun setUp() {
        viewModel = GameViewModel()
    }

    @Test
    fun handleScoreUpdateMessage_updatesPlayerScores_correctly() {
        // Given: JSON mit drei Spielern und verschiedenen Punkten
        val scoresArray = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "anna")
                put("score", 12)
            })
            put(JSONObject().apply {
                put("id", "ben")
                put("score", 8)
            })
            put(JSONObject().apply {
                put("id", "céline")
                put("score", 0)
            })
        }
        val json = JSONObject().apply {
            put("scores", scoresArray)
        }

        // When
        viewModel.handleScoreUpdateMessage(json)

        // Then
        val players = viewModel.players
        assertEquals(3, players.size)

        // Prüfe nur score-Feld für jeden Spieler
        assertTrue(players.any { it.id == "anna"   && it.score == 12 })
        assertTrue(players.any { it.id == "ben"    && it.score == 8 })
        assertTrue(players.any { it.id == "céline" && it.score == 0 })
    }

    @Test
    fun handleScoreUpdateMessage_overwritesExistingScores_onRepeatedCalls() {
        // Erstaufruf: anna=5, ben=7
        viewModel.handleScoreUpdateMessage(JSONObject().apply {
            put("scores", JSONArray().apply {
                put(JSONObject().put("id", "anna").put("score", 5))
                put(JSONObject().put("id", "ben").put("score", 7))
            })
        })
        // Zweitaufruf: anna=15, ben=3
        viewModel.handleScoreUpdateMessage(JSONObject().apply {
            put("scores", JSONArray().apply {
                put(JSONObject().put("id", "anna").put("score", 15))
                put(JSONObject().put("id", "ben").put("score", 3))
            })
        })

        // Now: scores should be updated (nicht summiert!)
        val anna = viewModel.players.find { it.id == "anna" }!!
        val ben  = viewModel.players.find { it.id == "ben" }!!
        assertEquals(15, anna.score)
        assertEquals(3,  ben.score)
    }
}
*/
