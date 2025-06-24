package at.se2_ss2025_gruppec.carcasonnefrontend

import at.se2_ss2025_gruppec.carcasonnefrontend.model.CreateGameRequest
import at.se2_ss2025_gruppec.carcasonnefrontend.model.CreateGameResponse
import at.se2_ss2025_gruppec.carcasonnefrontend.model.GameStateDTO
import at.se2_ss2025_gruppec.carcasonnefrontend.model.LoginRequest
import at.se2_ss2025_gruppec.carcasonnefrontend.model.RegisterRequest
import at.se2_ss2025_gruppec.carcasonnefrontend.model.TokenResponse
import at.se2_ss2025_gruppec.carcasonnefrontend.model.UserStatsDto
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

// Retrofit API interface for game
interface GameApi {
    @POST("api/game/create")
    suspend fun createGame(
        @Header("Authorization") token: String,
        @Body request: CreateGameRequest
    ): CreateGameResponse

    @GET("api/game/{gameId}")
    suspend fun getGame(
        @Header("Authorization") token: String,
        @Path("gameId") gameId: String
    ): GameStateDTO
}

// Retrofit API interface for auth
interface AuthApi {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest)
}

// Retrofit API interface for stats
interface StatsApi {
    @GET("api/game/stats")
    suspend fun getUserStats(
        @Header("Authorization") token: String
    ): UserStatsDto
}

// Singleton Retrofit client
object ApiClient {
    private val retrofit = Retrofit.Builder()
        .baseUrl("http://192.168.0.12:8080/") // Enter your local IP address instead of localhost (10.0.2.2) for real device demo!
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val gameApi: GameApi = retrofit.create(GameApi::class.java)
    val authApi: AuthApi = retrofit.create(AuthApi::class.java)
    val statsApi: StatsApi = retrofit.create(StatsApi::class.java)
}