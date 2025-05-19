package com.carcassonne.network

import com.carcassonne.model.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Interface für die API-Kommunikation mit dem Backend
 */
interface CarcassonneApi {


    /**
     * Gibt den aktuellen Spielzustand zurück
     */
    @GET("api/games/{id}")
    suspend fun getGameState(@Path("id") gameId: String): Response<GameStateDto>

    /**
     * Platziert ein Plättchen
     */
    @POST("api/games/{id}/placeTile")
    suspend fun placeTile(
        @Path("id") gameId: String,
        @Body placeTileDto: PlaceTileDto
    ): Response<Boolean>

}
