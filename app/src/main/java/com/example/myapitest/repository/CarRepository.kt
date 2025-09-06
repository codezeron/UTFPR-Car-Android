package com.example.myapitest.repository

import com.example.myapitest.model.Car
import com.example.myapitest.service.ApiService
import com.example.myapitest.service.Resource
import retrofit2.Response
import java.io.IOException

class CarRepository(private val apiService: ApiService) {

    // safeApiCall centraliza o tratamento de erros
    suspend fun <T> safeApiCall(
        call: suspend () -> Response<T>
    ): Resource<T> {
        try {
            val response = call()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    return Resource.Success(body)
                }
            }
            return Resource.Error("Erro na requisição: ${response.code()}")
        } catch (e: IOException) {
            return Resource.Error("Verifique sua conexão de internet.")
        } catch (e: Exception) {
            return Resource.Error("Um erro desconhecido ocorreu: ${e.message}")
        }
    }

    // Os métodos do repositório retornam Resource
    suspend fun getCars(): Resource<List<Car>> = safeApiCall { apiService.getCars() }
    suspend fun saveCar(car: Car): Resource<Car> = safeApiCall { apiService.saveCar(car) }
    suspend fun getCarById(id: String): Resource<Car> = safeApiCall { apiService.getCarById(id) }
    suspend fun deleteCar(id: String): Resource<Unit> = safeApiCall { apiService.deleteCar(id) }
    suspend fun updateCar(car: Car, id: String): Resource<Car> = safeApiCall { apiService.updateCar(
        id = id,
        car = car
    ) }
}