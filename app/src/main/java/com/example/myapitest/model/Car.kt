package com.example.myapitest.model

data class Car(
    val id: String?,
    val imageUrl: String,
    val year: String,
    val name: String,
    val licence: String,
    val place: LocationItem
)

data class LocationItem(
    val lat: Double = 0.0,
    val long: Double = 0.0
)
