package com.lmccallum.groupfinalproject.repository

import com.lmccallum.groupfinalproject.model.ScryfallCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CardRepository {
    private val apiService = ScryfallApiService.create()

    fun searchCardByName(cardName: String): Flow<ScryfallCard?> = flow {
        try {
            val card = apiService.getCardByName(cardName)
            emit(card)
        } catch (e: Exception) {
            emit(null)
        }
    }

    fun searchCardFromScan(ocrText: String): Flow<ScryfallCard?> = flow {
        val cardName = extractCardNameFromOCR(ocrText)
        if (cardName.isNotEmpty())
        {
            try {
                val card = apiService.getCardByName(cardName)
                emit(card)
            } catch (e: Exception) {
                emit(null)
            }
        } else
            emit(null)
    }

    private fun extractCardNameFromOCR(ocrText: String): String {
        return ocrText.lines()
            .firstOrNull { line ->
                line.isNotBlank() && line.length > 2
            }?.trim() ?: ""
    }
}