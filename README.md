# SgrPickup – Client Android App

Android app for users of **SgrPickup**, my bachelor's thesis project (Politehnica University of Timișoara, 2025). Users request a pickup for a sack of returnable bottles and cans (Romania's deposit-return system, SGR) so a driver can collect it.

Backend and system overview: [LicentaBackend](https://github.com/MarcAndreiFurtos/LicentaBackend)

## Features

- Login with Auth0
- Choose a pickup address with a location search bar, Google Maps and the device's location
- Enter the sack size and confirm the pickup details
- Add a payment card and choose one for a pickup
- Profile screen

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · ViewModel + Coroutines · Google Maps Compose & Play Services Location · Auth0 Android SDK

## Structure

```
app/src/main/java/com/licenta/licentaclientapp/
├── MainActivity.kt
└── ui/
    ├── Navigation.kt              splash → login → home → pickup / card / profile
    ├── LoginScreen.kt, AuthViewModel.kt, Auth0Manager.kt
    ├── HomeScreen.kt
    ├── PickupAddressScreen.kt, LocationSearchBar.kt, LocationUtils.kt
    ├── DebitCardScreen.kt, CardViewModel.kt
    └── ProfileScreen.kt
```

## Running

1. Open the project in Android Studio.
2. Set your Google Maps API key and Auth0 credentials. Keep them out of version control, for example in `local.properties`.
3. Run on an emulator or a device with Google Play services.
