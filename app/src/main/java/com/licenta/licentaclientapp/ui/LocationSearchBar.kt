package com.licenta.licentaclientapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.navigation.NavController


@Composable
fun LocationSearchBar(navController: NavController) {
    Box(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF2F2F2))
            .clickable {
                navController.navigate("pickup_address_screen")
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "Enter pickup address",
            color = Color.Gray,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}