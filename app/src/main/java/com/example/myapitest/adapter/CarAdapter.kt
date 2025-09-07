package com.example.myapitest.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapitest.R
import com.example.myapitest.model.Car
import com.squareup.picasso.Picasso

class CarAdapter(
    private var cars: List<Car>,
    private val onItemClick: (Car) -> Unit = {}
) : RecyclerView.Adapter<CarAdapter.CarViewHolder>() {
    class CarViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val modelTextView: TextView = view.findViewById(R.id.model)
        val yearTextView: TextView = view.findViewById(R.id.year)
        val licenseTextView: TextView = view.findViewById(R.id.license)
        val carImageView: ImageView = view.findViewById(R.id.image)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_car_layout, parent, false)
        return CarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CarViewHolder, position: Int) {
        val car = cars[position]
        holder.modelTextView.text = car.name
        holder.yearTextView.text = car.year
        holder.licenseTextView.text = car.licence
        if (car.imageUrl.isNotEmpty()) {
            Picasso.get()
                .load(car.imageUrl)
                .placeholder(R.drawable.car_replacement)
                .error(R.drawable.car_replacement)
                .into(holder.carImageView)
        } else {
            holder.carImageView.setImageResource(R.drawable.car_replacement)
        }

        holder.itemView.setOnClickListener {
            onItemClick(car)
        }
    }

    fun getCurrentList(): List<Car> = cars

    override fun getItemCount(): Int = cars.size

    fun addCar(newCar: Car) {
        cars = cars + newCar // Adiciona no final
        notifyItemInserted(cars.size - 1) // Anima a adição
    }

    fun updateData(newCars: List<Car>) {
        val oldList = this.cars.toMutableList()
        this.cars = newCars

        // Atualização inteligente - compara as listas
        if (oldList.size != newCars.size) {
            // Se o tamanho mudou, atualiza tudo
            notifyDataSetChanged()
        } else {
            // Se o tamanho é o mesmo, verifica item por item
            for (i in oldList.indices) {
                if (oldList[i] != newCars[i]) {
                    notifyItemChanged(i) // Atualiza apenas o item que mudou
                }
            }
        }
    }

    fun updateCarById(updatedCar: Car) {
        val index = cars.indexOfFirst { it.id == updatedCar.id }
        if (index != -1) {
            // Cria uma nova lista para evitar problemas de referência
            val newList = cars.toMutableList()
            newList[index] = updatedCar
            cars = newList
            notifyItemChanged(index)
        }
    }

    fun removeCar(carId: String) {
        val index = cars.indexOfFirst { it.id == carId }
        if (index != -1) {
            cars = cars.toMutableList().apply { removeAt(index) }
            notifyItemRemoved(index) // Anima a remoção
        }
    }
}