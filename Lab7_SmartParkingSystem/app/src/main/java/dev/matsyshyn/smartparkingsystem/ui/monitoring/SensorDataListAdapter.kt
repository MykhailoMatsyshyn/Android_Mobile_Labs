package dev.matsyshyn.smartparkingsystem.ui.monitoring

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.matsyshyn.smartparkingsystem.databinding.ItemSensorDataBinding
import dev.matsyshyn.smartparkingsystem.data.model.SensorData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SensorDataListAdapter : ListAdapter<SensorData, SensorDataListAdapter.SensorDataViewHolder>(SensorDataDiffCallback()) {
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SensorDataViewHolder {
        val binding = ItemSensorDataBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SensorDataViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SensorDataViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class SensorDataViewHolder(private val binding: ItemSensorDataBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(data: SensorData) {
            binding.tvTimestamp.text = dateFormat.format(Date(data.timestamp))
            binding.tvFreeSpots.text = "${data.freeSpots}"
            binding.tvCo.text = "${String.format("%.1f", data.coLevel)} ppm"
            binding.tvNox.text = "${String.format("%.1f", data.noxLevel)} ppm"
            binding.tvTemp.text = "${String.format("%.1f", data.temperature)}°C"
        }
    }
    
    class SensorDataDiffCallback : DiffUtil.ItemCallback<SensorData>() {
        override fun areItemsTheSame(oldItem: SensorData, newItem: SensorData): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: SensorData, newItem: SensorData): Boolean {
            return oldItem == newItem
        }
    }
}

