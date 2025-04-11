package at.se2_ss2025_gruppec.carcasonnefrontend

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

data class GameStateDTO(
    val gameId: String,
    val players: List<String>,
    val status: String,
    val currentPlayerIndex: Int
)

data class CreateGameRequest(val playerCount: Int, val hostName: String)
data class CreateGameResponse(val gameId: String)

//Retrofit API interface
interface GameApi {
    @POST("api/game/create")
    suspend fun createGame(@Body request: CreateGameRequest): CreateGameResponse

    @GET("api/game/{gameId}")
    suspend fun getGame(@Path("gameId") gameId: String): GameStateDTO
}

//Singleton Retrofit client
object ApiClient {
    val retrofit: GameApi = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8080/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GameApi::class.java)
}