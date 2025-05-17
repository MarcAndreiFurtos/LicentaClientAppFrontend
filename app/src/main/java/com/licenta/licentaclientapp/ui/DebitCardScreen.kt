package com.licenta.licentaclientapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebitCardScreen(
    onBackPressed: () -> Unit,
    onSaveCard: (CardDetails) -> Unit
) {
    // State for form fields
    var cardholderName by remember { mutableStateOf("") }
    var cardNumber by remember { mutableStateOf("") }
    var expirationMonth by remember { mutableStateOf("") }
    var expirationYear by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var iban by remember { mutableStateOf("") }

    // State for field validation
    var cardholderNameError by remember { mutableStateOf<String?>(null) }
    var cardNumberError by remember { mutableStateOf<String?>(null) }
    var expirationError by remember { mutableStateOf<String?>(null) }
    var cvvError by remember { mutableStateOf<String?>(null) }
    var ibanError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Debit Card") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Icon and Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreditCard,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Card Details",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Cardholder Name Field
            OutlinedTextField(
                value = cardholderName,
                onValueChange = {
                    cardholderName = it
                    cardholderNameError = if (it.isBlank()) "Cardholder name is required" else null
                },
                label = { Text("Cardholder Name") },
                modifier = Modifier.fillMaxWidth(),
                isError = cardholderNameError != null,
                supportingText = { cardholderNameError?.let { Text(it) } }
            )

            // Card Number Field
            OutlinedTextField(
                value = cardNumber,
                onValueChange = {
                    if (it.length <= 16 && it.all { char -> char.isDigit() }) {
                        cardNumber = it
                        cardNumberError = when {
                            it.isBlank() -> "Card number is required"
                            it.length < 16 -> "Card number must be 16 digits"
                            else -> null
                        }
                    }
                },
                label = { Text("Card Number") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = cardNumberError != null,
                supportingText = { cardNumberError?.let { Text(it) } }
            )

            // Expiration Date and CVV Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Expiration Month
                OutlinedTextField(
                    value = expirationMonth,
                    onValueChange = {
                        if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                            expirationMonth = it
                            // Validate month
                            expirationError = validateExpirationDate(it, expirationYear)
                        }
                    },
                    label = { Text("MM") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = expirationError != null
                )

                // Expiration Year
                OutlinedTextField(
                    value = expirationYear,
                    onValueChange = {
                        if (it.length <= 2 && it.all { char -> char.isDigit() }) {
                            expirationYear = it
                            // Validate year
                            expirationError = validateExpirationDate(expirationMonth, it)
                        }
                    },
                    label = { Text("YY") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = expirationError != null
                )

                // CVV
                OutlinedTextField(
                    value = cvv,
                    onValueChange = {
                        if (it.length <= 3 && it.all { char -> char.isDigit() }) {
                            cvv = it
                            cvvError = when {
                                it.isBlank() -> "CVV is required"
                                it.length < 3 -> "CVV must be 3 digits"
                                else -> null
                            }
                        }
                    },
                    label = { Text("CVV") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = cvvError != null,
                    supportingText = { cvvError?.let { Text(it) } }
                )
            }

            // Display error for expiration date if any
            expirationError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }

            // IBAN Field
            OutlinedTextField(
                value = iban,
                onValueChange = {
                    iban = it.uppercase(Locale.getDefault())
                    ibanError = when {
                        it.isBlank() -> "IBAN is required"
                        !isValidIban(it) -> "Please enter a valid IBAN"
                        else -> null
                    }
                },
                label = { Text("IBAN") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = ibanError != null,
                supportingText = { ibanError?.let { Text(it) } }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Save Button
            Button(
                onClick = {
                    // Validate all fields
                    cardholderNameError = if (cardholderName.isBlank()) "Cardholder name is required" else null
                    cardNumberError = when {
                        cardNumber.isBlank() -> "Card number is required"
                        cardNumber.length < 16 -> "Card number must be 16 digits"
                        else -> null
                    }
                    expirationError = validateExpirationDate(expirationMonth, expirationYear)
                    cvvError = when {
                        cvv.isBlank() -> "CVV is required"
                        cvv.length < 3 -> "CVV must be 3 digits"
                        else -> null
                    }
                    ibanError = when {
                        iban.isBlank() -> "IBAN is required"
                        !isValidIban(iban) -> "Please enter a valid IBAN"
                        else -> null
                    }

                    // If all validations pass, save the card
                    if (cardholderNameError == null && cardNumberError == null &&
                        expirationError == null && cvvError == null && ibanError == null) {
                        onSaveCard(
                            CardDetails(
                                cardholderName = cardholderName,
                                cardNumber = cardNumber,
                                expirationMonth = expirationMonth,
                                expirationYear = expirationYear,
                                cvv = cvv,
                                iban = iban
                            )
                        )
                        onBackPressed()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Card", fontSize = 16.sp)
            }
        }
    }
}

// Data class to hold card details
data class CardDetails(
    val cardholderName: String,
    val cardNumber: String,
    val expirationMonth: String,
    val expirationYear: String,
    val cvv: String,
    val iban: String
)

// Function to validate expiration date
private fun validateExpirationDate(month: String, year: String): String? {
    if (month.isBlank() || year.isBlank()) {
        return "Expiration date is required"
    }

    try {
        val monthInt = month.toInt()
        val yearInt = year.toInt()
        val currentYear = Calendar.getInstance().get(Calendar.YEAR) % 100
        val currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1

        return when {
            monthInt < 1 || monthInt > 12 -> "Invalid month"
            yearInt < currentYear -> "Card has expired"
            yearInt == currentYear && monthInt < currentMonth -> "Card has expired"
            else -> null
        }
    } catch (e: NumberFormatException) {
        return "Invalid expiration date"
    }
}

// Simple IBAN validation
private fun isValidIban(iban: String): Boolean {
    // This is a very basic validation
    // In a real app, you would want a more comprehensive validation
    return iban.length >= 15 && iban.length <= 34
}