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

data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val password: String)
data class TokenResponse(val token: String)

//Retrofit API interface for game
interface GameApi {
    @POST("api/game/create")
    suspend fun createGame(
        @Header("Authorization") token: String,
        @Body request: CreateGameRequest
    ): CreateGameResponse

    @GET("api/game/{gameId}")
    suspend fun getGame(
        @Header("Authorization") token: String,
        @Path("gameId") gameId: String)
    : GameStateDTO
}

//Retrofit API interface for auth
interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest) //No need to get HTTP 201 from backend, if needed: create data class MessageResponse and set as return type
}

//Singleton Retrofit client
object ApiClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://10.0.2.2:8080/") //Use local ip address for real device demo
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val gameApi: GameApi = retrofit.create(GameApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
}