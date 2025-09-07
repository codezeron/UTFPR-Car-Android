package com.example.myapitest

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myapitest.databinding.ActivityAddEditCarBinding
import com.example.myapitest.model.Car
import com.example.myapitest.model.LocationItem
import com.example.myapitest.repository.CarRepository
import com.example.myapitest.service.FirebaseStorageService
import com.example.myapitest.service.Resource
import com.example.myapitest.service.RetrofitClient
import com.squareup.picasso.Picasso
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.SecureRandom


class AddEditCarActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddEditCarBinding
    private val carRepository = CarRepository(RetrofitClient.apiService)
    private var selectedImageUri: Uri? = null
    private var currentCar: Car? = null
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0

    companion object {
        private const val EXTRA_CAR_ID = "car_id"
        private const val EXTRA_CAR_NAME = "car_name"
        private const val EXTRA_CAR_YEAR = "car_year"
        private const val EXTRA_CAR_LICENCE = "car_licence"
        private const val EXTRA_CAR_IMAGE_URL = "car_image_url"
        private const val EXTRA_CAR_LAT = "car_lat"
        private const val EXTRA_CAR_LNG = "car_lng"
        private const val EXTRA_NEW_CAR = "is_new_car"
        private const val LAT = "latitude"
        private const val LNG = "longitude"
        private const val TAG = "ActivityAddEditCar"

        fun newIntent(context: Context, car: Car? = null): Intent {
            return Intent(context, AddEditCarActivity::class.java).apply {
                car?.let {
                    putExtra(EXTRA_CAR_ID, it.id)
                    putExtra(EXTRA_CAR_NAME, it.name)
                    putExtra(EXTRA_CAR_YEAR, it.year)
                    putExtra(EXTRA_CAR_LICENCE, it.licence)
                    putExtra(EXTRA_CAR_IMAGE_URL, it.imageUrl)
                    putExtra(EXTRA_CAR_LAT, it.place.lat)
                    putExtra(EXTRA_CAR_LNG, it.place.long)
                }
            }
        }
    }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
            uri?.let {
                selectedImageUri = it

                Picasso.get()
                    .load(it)
                    .placeholder(R.drawable.car_replacement)
                    .into(binding.imagePreview)
            }

    }

    private val locationPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.let { data ->
                data.getDoubleExtra(LAT, 0.0).let { lat ->
                    latitude = lat
                }
                data.getDoubleExtra(LNG, 0.0).let { long ->
                    longitude = long
                }
            }
            updateLocationText()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddEditCarBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Ler os dados individuais do Intent
        val carId = intent.getStringExtra(EXTRA_CAR_ID)
        val carName = intent.getStringExtra(EXTRA_CAR_NAME)
        val carYear = intent.getStringExtra(EXTRA_CAR_YEAR)
        val carLicence = intent.getStringExtra(EXTRA_CAR_LICENCE)
        val carImageUrl = intent.getStringExtra(EXTRA_CAR_IMAGE_URL)
        val carLat = intent.getDoubleExtra(EXTRA_CAR_LAT, 0.0)
        val carLng = intent.getDoubleExtra(EXTRA_CAR_LNG, 0.0)

        // Se algum dado existir, é modo edição
        if (carName != null || carId != null) {
            currentCar = Car(
                id = carId,
                name = carName ?: "",
                year = carYear ?: "",
                licence = carLicence ?: "",
                imageUrl = carImageUrl ?: "",
                place = LocationItem(lat = carLat, long = carLng)
            )
        }

        setupView()
        setupDeteleButton()
        loadCarData()
    }

    private fun setupDeteleButton() {
        binding.deleteButton.visibility = if (currentCar == null) View.GONE else View.VISIBLE
        binding.deleteButton.setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun setupView() {
        binding.selectImageButton.setOnClickListener {
            openImagePicker()
        }

        binding.selectLocationButton.setOnClickListener {
            openMapPicker()
        }

        binding.saveButton.setOnClickListener {
            saveCar()
        }
    }

    private fun loadCarData() {
        currentCar?.let { car ->
            binding.nameEditText.setText(car.name)
            binding.yearEditText.setText(car.year)
            binding.licenseEditText.setText(car.licence)
            latitude = car.place.lat
            longitude = car.place.long
            updateLocationText()

            if (car.imageUrl.isNotEmpty()) {
                Picasso.get()
                    .load(car.imageUrl)
                    .placeholder(R.drawable.car_replacement)
                    .into(binding.imagePreview)
            } else {
                binding.imagePreview.setImageResource(R.drawable.car_replacement)
            }
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.confirmar_exclusao)
            .setMessage(R.string.confirmar_exclusao_mensagem)
            .setPositiveButton(getString(R.string.excluir)) { dialog, _ ->
                deleteCar()
                dialog.dismiss()
            }
            .setNeutralButton (getString(R.string.cancelar)){ dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .show()
    }

    private fun openImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun openMapPicker() {
        val intent = MapPickerActivity.newIntent(this, latitude, longitude)
        locationPickerLauncher.launch(intent)
    }

    private fun updateLocationText() {
        binding.locationTextView.text = "Lat: %.6f, Long: %.6f".format(latitude, longitude)
    }


    private fun saveCar() {
        val name = binding.nameEditText.text.toString().trim()
        val year = binding.yearEditText.text.toString().trim()
        val license = binding.licenseEditText.text.toString().trim()

        if (name.isEmpty() || year.isEmpty() || license.isEmpty()) {
            showToast("Preencha todos os campos")
            return
        }

        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            try { 
                var imageUrl = currentCar?.imageUrl ?: ""

                if (selectedImageUri != null) {
                    // Upload da nova imagem se foi selecionada
                    if (!FirebaseStorageService.isFirebaseStorageUrl(selectedImageUri.toString())) {
                        imageUrl = selectedImageUri.toString()
                    } else {
                        // deletar imagem antiga se existir
                        if(currentCar?.imageUrl?.isNotEmpty() == true){
                            try {
                                FirebaseStorageService.deleteImage(currentCar!!.imageUrl)
                            } catch (e: Exception) {
                                Log.e(TAG, "Erro ao deletar a imagem antiga ${e.message}")
                            }
                        }
                    }
                    //fazer o upload de uma nova imagem caso for um arquivo local
                    imageUrl = FirebaseStorageService.safeUploadImage(selectedImageUri!!, this@AddEditCarActivity)
                }


                val car = Car(
                    id = SecureRandom.getInstanceStrong().nextInt().toString(),
                    name = name,
                    year = year,
                    licence = license,
                    imageUrl = imageUrl,
                    place = LocationItem(lat = latitude, long = longitude)
                )

                // Salvar/atualizar carro
                val result = if (currentCar == null) {
                    carRepository.saveCar(car)
                } else {
                    currentCar?.id?.let { id ->
                       carRepository.updateCar(car = car, id = id)
                    } ?: throw Exception("ID do carro não encontrado")
                }

                withContext(Dispatchers.Main) {
                    showLoading(false)
                    handleSaveResult(result, car)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showToast(e.message.toString())
                }
            }
        }
    }

    private fun handleSaveResult(result: Resource<Car>, savedCar: Car) {
        when (result) {
            is Resource.Success -> {
                setResult(RESULT_OK, Intent().apply {
                    putExtra(EXTRA_CAR_ID, savedCar.id)
                    putExtra(EXTRA_CAR_NAME, savedCar.name)
                    putExtra(EXTRA_CAR_YEAR, savedCar.year)
                    putExtra(EXTRA_CAR_LICENCE, savedCar.licence)
                    putExtra(EXTRA_CAR_IMAGE_URL, savedCar.imageUrl)
                    putExtra(EXTRA_CAR_LAT, savedCar.place.lat)
                    putExtra(EXTRA_CAR_LNG, savedCar.place.long)
                    putExtra(EXTRA_NEW_CAR, currentCar == null) // Chave única para decisão
                })
                showToast("Carro salvo com sucesso!")
                finish()
            }
            is Resource.Error -> {
                showToast(result.message.toString())
            }
        }
    }

    private fun deleteCar() {
        var carId = currentCar?.id ?: return

        showLoading(true)


        CoroutineScope(Dispatchers.IO).launch {

            try {
                firebaseStorageDelete()

                val result = carRepository.deleteCar(carId)

                withContext(Dispatchers.Main) {
                    showLoading(false)

                    when (result) {
                        is Resource.Success<*> -> {
                            showToast(getString(R.string.carro_excluido_com_sucesso))
                            val resultIntent = Intent().apply {
                                //operacao exclusiva para deletar
                                putExtra("operation", "delete")
                                putExtra("car_id", currentCar?.id)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        }
                        is Resource.Error<*> -> {
                            showToast(result.message.toString())
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    showToast(e.message.toString())
                }
            }
        }
    }

    private suspend fun firebaseStorageDelete() {
        if (currentCar?.imageUrl?.isNotEmpty() == true) {
            try {
                FirebaseStorageService.deleteImage(currentCar!!.imageUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao deletar imagem antiga: ${e.message}")
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) android.view.View.VISIBLE else android.view.View.GONE
        binding.saveButton.isEnabled = !show
        binding.selectImageButton.isEnabled = !show
        binding.selectLocationButton.isEnabled = !show
    }

}
