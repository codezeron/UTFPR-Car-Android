package com.example.myapitest.service

import com.example.myapitest.model.Car
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ApiService {
    @GET("car")
    suspend fun getCars(): Response<List<Car>>

    @POST("car")
    suspend fun saveCar(@Body car: Car): Response<Car>

    @GET("car/{id}" )
    suspend fun getCarById(@Path("id") id: String): Response<Car>

    @DELETE("car/{id}" )
    suspend fun deleteCar(@Path("id") id: String): Response<Unit>

    @PATCH("car/{id}" )
    suspend fun updateCar(@Path("id") id: String, @Body car: Car): Response<Car>

}
