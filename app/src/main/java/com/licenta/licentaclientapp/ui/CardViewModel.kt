package com.licenta.licentaclientapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CardViewModel : ViewModel() {
    // MutableStateFlow to hold the card details
    private val _cardDetails = MutableStateFlow<CardDetails?>(null)
    val cardDetails: StateFlow<CardDetails?> = _cardDetails.asStateFlow()

    // Boolean flag to indicate if a card is saved
    private val _hasCardSaved = MutableStateFlow(false)
    val hasCardSaved: StateFlow<Boolean> = _hasCardSaved.asStateFlow()

    // Function to save card details
    fun saveCardDetails(details: CardDetails) {
        _cardDetails.value = details
        _hasCardSaved.value = true

        // In a real application, you would likely save this to a database
        // or shared preferences for persistence
    }

    // Function to clear card details
    fun clearCardDetails() {
        _cardDetails.value = null
        _hasCardSaved.value = false
    }
}