package at.se2_ss2025_gruppec.carcasonnefrontend

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class CreateGameRequest(val playerCount: Int)
data class CreateGameResponse(val gameId: String)

//Retrofit API interface
interface GameApi {
    @POST("api/game/create")
    suspend fun createGame(@Body request: CreateGameRequest): CreateGameResponse
}

//Singleton Retrofit client
object ApiClient {
    val retrofit: GameApi = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8080/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GameApi::class.java)
}