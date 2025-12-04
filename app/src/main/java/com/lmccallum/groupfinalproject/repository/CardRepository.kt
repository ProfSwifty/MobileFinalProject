package com.lmccallum.groupfinalproject.repository

import com.lmccallum.groupfinalproject.model.ScryfallCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CardRepository {
    private val apiService = ScryfallApiService.create()

    //Method to search scryfalls API for card by its name
    fun searchCardByName(cardName: String): Flow<ScryfallCard?> = flow {
        try {
            val card = apiService.getCardByName(cardName)
            emit(card)
        } catch (e: Exception) {
            emit(null)
        }
    }
}