//package com.licenta.licentaclientapp.ui
//
//import androidx.compose.foundation.background
//import androidx.compose.foundation.clickable
//import androidx.compose.foundation.layout.*
//import androidx.compose.foundation.shape.RoundedCornerShape
//import androidx.compose.material.icons.Icons
//import androidx.compose.material.icons.filled.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.Alignment
//import androidx.compose.ui.Modifier
//import androidx.compose.ui.graphics.Color
//import androidx.compose.ui.text.font.FontWeight
//import androidx.compose.ui.unit.dp
//import androidx.compose.ui.unit.sp
//import androidx.navigation.NavController
//
//@Composable
//fun TopBar(onLogout: () -> Unit, navController: NavController) {
//    var showMenu by remember { mutableStateOf(false) }
//
//    Row(
//        Modifier
//            .fillMaxWidth()
//            .padding(16.dp),
//        verticalAlignment = Alignment.CenterVertically,
//        horizontalArrangement = Arrangement.SpaceBetween
//    ) {
//        Text(
//            text = "Welcome to Sgr Pickup",
//            fontSize = 20.sp,
//            fontWeight = FontWeight.Bold
//        )
//
//        // Add menu button with dropdown
//        Box {
//            IconButton(onClick = { showMenu = true }) {
//                Icon(
//                    imageVector = Icons.Default.AccountCircle,
//                    contentDescription = "Account"
//                )
//            }
//
//            DropdownMenu(
//                expanded = showMenu,
//                onDismissRequest = { showMenu = false }
//            ) {
//                DropdownMenuItem(
//                    text = { Text("My Profile") },
//                    onClick = {
//                        showMenu = false
//                        navController.navigate("profile_screen")
//                    },
//                    leadingIcon = {
//                        Icon(
//                            imageVector = Icons.Default.Person,
//                            contentDescription = "Profile"
//                        )
//                    }
//                )
//
//                DropdownMenuItem(
//                    text = { Text("Log Out") },
//                    onClick = {
//                        showMenu = false
//                        onLogout()
//                    },
//                    leadingIcon = {
//                        Icon(
//                            imageVector = Icons.Default.ExitToApp,
//                            contentDescription = "Log Out"
//                        )
//                    }
//                )
//            }
//        }
//    }
//}
//
//@Composable
//fun LocationSearchBar(navController: NavController) {
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp, vertical = 8.dp)
//            .clickable {
//                // Navigate to search screen when clicked
//                navController.navigate("search_screen")
//            },
//        shape = RoundedCornerShape(8.dp)
//    ) {
//        Row(
//            modifier = Modifier
//                .fillMaxWidth()
//                .padding(16.dp),
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//            Icon(
//                imageVector = Icons.Default.Search,
//                contentDescription = "Search",
//                tint = Color.Gray
//            )
//
//            Spacer(modifier = Modifier.width(8.dp))
//
//            Text(
//                text = "Where to?",
//                color = Color.Gray,
//                fontSize = 16.sp
//            )
//        }
//    }
//}
//
//@Composable
//fun SavedAddresses() {
//    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
//        SavedAddressItem("Home", "123 Main Street")
//        SavedAddressItem("Work", "456 Office Rd")
//    }
//}
//
//@Composable
//fun SavedAddressItem(title: String, address: String) {
//    Row(
//        Modifier
//            .fillMaxWidth()
//            .padding(vertical = 8.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Icon(
//            imageVector = Icons.Default.Place,
//            contentDescription = null,
//            tint = Color.Gray,
//            modifier = Modifier.size(24.dp)
//        )
//        Column(Modifier.padding(start = 8.dp)) {
//            Text(text = title, fontWeight = FontWeight.Bold)
//            Text(text = address, color = Color.Gray, fontSize = 12.sp)
//        }
//    }
//}
//
//@Composable
//fun AddDebitCardButton(navController: NavController) {
//    Box(
//        Modifier
//            .fillMaxWidth()
//            .padding(horizontal = 16.dp, vertical = 12.dp)
//    ) {
//        Button(
//            onClick = {
//                // Navigate to DebitCardScreen
//                navController.navigate("debit_card_screen")
//            },
//            modifier = Modifier.fillMaxWidth(),
//            shape = RoundedCornerShape(12.dp)
//        ) {
//            Text("Add Debit Card", fontSize = 16.sp, modifier = Modifier.padding(8.dp))
//        }
//    }
//}
//
//@Composable
//fun CallToActionButton(navController: NavController) {
//    Box(
//        modifier = Modifier
//            .fillMaxWidth()
//            .background(Color.White)
//            .padding(16.dp),
//        contentAlignment = Alignment.Center
//    ) {
//        Button(
//            onClick = {
//                // Navigate to booking screen
//                navController.navigate("booking_screen")
//            },
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(56.dp),
//            shape = RoundedCornerShape(28.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = MaterialTheme.colorScheme.primary
//            )
//        ) {
//            Text(
//                "Request Pickup",
//                fontSize = 18.sp,
//                fontWeight = FontWeight.Bold
//            )
//        }
//    }
//}