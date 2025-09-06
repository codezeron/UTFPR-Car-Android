package com.example.myapitest

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.myapitest.database.DatabaseBuilder
import com.example.myapitest.databinding.ActivityMapPickerBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapPickerActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityMapPickerBinding
    private lateinit var googleMap: GoogleMap
    private var selectedLocation: LatLng? = null
    private var initialLatitude: Double = 0.0
    private var initialLongitude: Double = 0.0


    override fun onCreate(savedInstanceState: Bundle?, ) {
        super.onCreate(savedInstanceState)
        binding = ActivityMapPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)


        initialLatitude = intent.getDoubleExtra(LAT, 0.0)
        initialLongitude = intent.getDoubleExtra(LNG, 0.0)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.confirmButton.setOnClickListener {
            confirmLocation()
        }

        setupLocationDefault()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        val initialLocation = if (initialLatitude != 0.0 && initialLongitude != 0.0) {
            LatLng(initialLatitude, initialLongitude)
        } else {
            // Fallback enquanto carrega do banco
            LatLng(-23.5505, -46.6333)
        }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLocation, 12f))

        selectedLocation = initialLocation
        googleMap.addMarker(
            MarkerOptions().position(initialLocation)
        )

        googleMap.setOnMapClickListener { latLng ->
            selectedLocation = latLng
            googleMap.clear()
            googleMap.addMarker(
                MarkerOptions().position(latLng)
            )
        }
    }


    private fun setupLocationDefault() {

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lastLocation = DatabaseBuilder.getInstance().userLocationDao().getLastLocation()
                Log.d(TAG, "Last location from DB: $lastLocation")

                withContext(Dispatchers.Main) {
                    if (initialLatitude == 0.0 && initialLongitude == 0.0) {
                        if (lastLocation != null && lastLocation.latitude != 0.0 && lastLocation.longitude != 0.0) {
                            initialLatitude = lastLocation.latitude
                            initialLongitude = lastLocation.longitude
                            updateMapIfReady()
                        } else {
                            initialLatitude = -23.5505
                            initialLongitude = -46.6333
                            updateMapIfReady()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao carregar a localização do banco: ${e.message}")
            }
        }
    }

    private fun updateMapIfReady() {
        if (::googleMap.isInitialized) {
            val newLocation = LatLng(initialLatitude, initialLongitude)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 12f))
            googleMap.clear()
            googleMap.addMarker(
                MarkerOptions().position(newLocation)
            )
            selectedLocation = newLocation
        }
    }

    private fun confirmLocation() {
        selectedLocation?.let { location ->
            val resultIntent = Intent().apply {
                putExtra(LAT, location.latitude)
                putExtra(LNG, location.longitude)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        } ?: run {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    companion object {
        private const val LAT = "latitude"
        private const val LNG = "longitude"
        private const val TAG = "MapPickerActivity"
        fun newIntent(context: Context, lat: Double = 0.0, lng: Double = 0.0): Intent {
            return Intent(context, MapPickerActivity::class.java).apply {
                putExtra(LAT, lat)
                putExtra(LNG, lng)
            }
        }
    }
}