package com.lmccallum.groupfinalproject.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lmccallum.groupfinalproject.model.ScryfallCard
import com.lmccallum.groupfinalproject.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CardDetailViewModel : ViewModel() {

    private val repository = CardRepository()

    private val _currentCard = MutableStateFlow<ScryfallCard?>(null)
    val currentCard: StateFlow<ScryfallCard?> = _currentCard.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()


    //Lets user know if the card isn't a thing or they spelt it wrong
    fun searchCard(cardName: String) {
        if (cardName.isBlank()) return

        _isLoading.value = true
        _searchError.value = null
        _currentCard.value = null

        viewModelScope.launch {
            repository.searchCardByName(cardName).collect { card ->
                _currentCard.value = card
                _isLoading.value = false

                if (card == null)
                    _searchError.value = "Card '$cardName' either doesn't exist or is spelled wrong"
            }
        }
    }

    //Same thing as the other function but for scanning
    fun searchCardFromScan(ocrText: String)
    {
        _isLoading.value = true
        _searchError.value = null
        _currentCard.value = null

        viewModelScope.launch {
            repository.searchCardFromScan(ocrText).collect { card ->
                _currentCard.value = card
                _isLoading.value = false

                if (card == null)
                    _searchError.value = "Could not find card from scanned text"
            }
        }
    }
}