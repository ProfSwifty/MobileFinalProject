package com.lmccallum.groupfinalproject.repository

import com.lmccallum.groupfinalproject.model.ScryfallCard
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface ScryfallApiService {

    @GET("cards/named")
    suspend fun getCardByName(
        @Query("exact") cardName: String
    ): ScryfallCard

    companion object
    {
        private const val BASE_URL = "https://api.scryfall.com/"

        fun create(): ScryfallApiService
        {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ScryfallApiService::class.java)
        }
    }
}