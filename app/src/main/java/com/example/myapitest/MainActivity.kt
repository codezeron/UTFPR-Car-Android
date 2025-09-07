package com.example.myapitest

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.myapitest.adapter.CarAdapter
import com.example.myapitest.database.DatabaseBuilder
import com.example.myapitest.database.model.UserLocation
import com.example.myapitest.databinding.ActivityMainBinding
import com.example.myapitest.model.Car
import com.example.myapitest.model.LocationItem
import com.example.myapitest.repository.CarRepository
import com.example.myapitest.service.Resource
import com.example.myapitest.service.Resource.Success
import com.example.myapitest.service.RetrofitClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var carAdapter: CarAdapter
    private val carRepository = CarRepository(RetrofitClient.apiService)
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>

    private val addEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (RESULT_OK == result.resultCode) {
            result.data?.let { data ->
                handleCarResult(data)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        auth = FirebaseAuth.getInstance()


        // 1- Criar tela de Login com algum provedor do Firebase (Telefone, Google)
        //      Cadastrar o Seguinte celular para login de test: +5511912345678
        //      Código de verificação: 101010

        // 2- Criar Opção de Logout no aplicativo

        // 3- Integrar API REST /car no aplicativo
        //      API será disponibilida no Github
        //      JSON Necessário para salvar e exibir no aplicativo
        //      O Image Url deve ser uma foto armazenada no Firebase Storage
        //      { "id": "001", "imageUrl":"https://image", "year":"2020/2020", "name":"Gaspar", "licence":"ABC-1234", "place": {"lat": 0, "long": 0} }

        // Opcionalmente trabalhar com o Google Maps ara enviar o place
        if (auth.currentUser == null) {
            goToLogin()
            return
        }
        setupView()
        requestLocationPermission()
    }

    private fun goToLogin() {
        val intent = LoginActivity.newIntent(this)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        fetchCars()
    }

    private fun setupView() {
        setupSwipeToRefresh()
        setupRecyclerView()
        setupAddButton()
        setupLogoutButton()
    }

    private fun setupRecyclerView() {
        recyclerView = binding.recyclerView
        recyclerView.layoutManager = LinearLayoutManager(this)
        carAdapter = CarAdapter(emptyList()) { car ->
            openEditCarScreen(car)
        }
        recyclerView.adapter = carAdapter
    }

    private fun setupSwipeToRefresh() {
        swipeRefreshLayout = binding.swipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener {
            fetchCars()
        }
    }

    private fun setupLogoutButton() {
        val logoutButton = binding.logoutButton
        logoutButton.setOnClickListener {
            onLogout()
        }
    }

    private fun setupAddButton() {
        val addButton = binding.addCta
        addButton.setOnClickListener {
            openAddCarScreen()
        }
    }

    private fun requestLocationPermission() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        locationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (isGranted) {
                    getLastLocation()
                } else {
                    showToast("Permissão negada!")
                }
            }
        checkLocationPermissionAndRequest()
    }

    private fun checkLocationPermissionAndRequest() {
        when {
            checkSelfPermission(ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
                    && ContextCompat.checkSelfPermission(
                this,
                ACCESS_COARSE_LOCATION
            ) == PERMISSION_GRANTED
                -> {
                getLastLocation()
            }

            shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION) -> {
                locationPermissionLauncher.launch(ACCESS_FINE_LOCATION)
            }

            shouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION) -> {
                locationPermissionLauncher.launch(ACCESS_COARSE_LOCATION)
            }

            else -> {
                locationPermissionLauncher.launch(ACCESS_FINE_LOCATION)
            }
        }
    }

    private fun getLastLocation() {
        if (checkSelfPermission(ACCESS_FINE_LOCATION) != PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, ACCESS_COARSE_LOCATION) != PERMISSION_GRANTED
        ) {
            requestLocationPermission()
            return
        }
        fusedLocationProviderClient.lastLocation.addOnCompleteListener { task: Task<Location> ->
            if (task.isSuccessful) {
                val location = task.result
                val userLocation = UserLocation(
                    latitude = location?.latitude ?: 0.0,
                    longitude = location?.longitude ?: 0.0
                )

                CoroutineScope(Dispatchers.IO).launch {
                    DatabaseBuilder.getInstance().userLocationDao().insert(userLocation)
                    Log.d(TAG, "User location saved: $userLocation")
                }
            } else {
                showToast("Erro desconhecido")
            }
        }
    }

    private fun fetchCars() {
        CoroutineScope(Dispatchers.IO).launch {
            val result = carRepository.getCars()
            withContext(Dispatchers.Main) {
                binding.swipeRefreshLayout.isRefreshing = false
                when (result) {
                    is Success -> {
                        result.data?.let { cars ->
                            carAdapter.updateData(cars)
                        }
                    }

                    is Resource.Error -> {
                        showToast("Erro ao carregar dados: ${result.message}")
                    }
                }
            }
        }
    }

    private fun openAddCarScreen() {
        val intent = AddEditCarActivity.newIntent(this, null)
        addEditLauncher.launch(intent)
    }

    private fun openEditCarScreen(car: Car) {
        val intent = AddEditCarActivity.newIntent(this, car)
        addEditLauncher.launch(intent)
    }

    private fun handleCarResult(data: Intent) {
        when (data.getStringExtra("operation")) {
            "delete" -> {
                val carId = data.getStringExtra("car_id")
                carId?.let {
                    carAdapter.removeCar(it)
                    showToast(R.string.carro_excluido_com_sucesso.toString())
                }
            }

            else -> {
                val car = createCarFromIntent(data)
                val isNewCar = data.getBooleanExtra(EXTRA_NEW_CAR, true)

                if (isNewCar) {
                    addCarToList(car)
                } else {
                    updateCarInList(car)
                }
            }
        }
    }

    private fun createCarFromIntent(data: Intent): Car {
        return Car(
            id = data.getStringExtra(EXTRA_CAR_ID),
            name = data.getStringExtra(EXTRA_CAR_NAME) ?: "",
            year = data.getStringExtra(EXTRA_CAR_YEAR) ?: "",
            licence = data.getStringExtra(EXTRA_CAR_LICENCE) ?: "",
            imageUrl = data.getStringExtra(EXTRA_CAR_IMAGE_URL) ?: "",
            place = LocationItem(
                lat = data.getDoubleExtra(EXTRA_CAR_LAT, 0.0),
                long = data.getDoubleExtra(EXTRA_CAR_LNG, 0.0)
            ),
        )
    }

    // as 2 funções abaixo evitam uma nova chamada na API
    private fun addCarToList(car: Car) {
        val currentList = carAdapter.getCurrentList().toMutableList()
        currentList.add(0, car)
        carAdapter.addCar(car)
        binding.recyclerView.smoothScrollToPosition(0)
        showToast("Carro adicionado com sucesso!")
    }

    private fun updateCarInList(car: Car) {
        val currentList = carAdapter.getCurrentList().toMutableList()
        val index = currentList.indexOfFirst { it.id == car.id }
        if (index != -1) {
            currentList.add(index, car)
            carAdapter.updateCarById(car)
            showToast("Carro adicionado com sucesso!")
        }
    }

    private fun onLogout() {
        auth.signOut()
        val intent = LoginActivity.newIntent(this)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        showToast("Logout realizado com sucesso.")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_CAR_ID = "car_id"
        private const val EXTRA_CAR_NAME = "car_name"
        private const val EXTRA_CAR_YEAR = "car_year"
        private const val EXTRA_CAR_LICENCE = "car_licence"
        private const val EXTRA_CAR_IMAGE_URL = "car_image_url"
        private const val EXTRA_CAR_LAT = "car_lat"
        private const val EXTRA_CAR_LNG = "car_lng"
        private const val EXTRA_NEW_CAR = "is_new_car"
        private const val TAG = "MainActivity"

        fun newIntent(context: Context) = Intent(context, MainActivity::class.java)
    }
}
