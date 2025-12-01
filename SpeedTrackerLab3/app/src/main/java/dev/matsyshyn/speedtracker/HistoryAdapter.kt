package dev.matsyshyn.speedtracker

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class HistoryAdapter(private var dataList: List<TrackingData>) :
    RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDate: TextView = itemView.findViewById(R.id.tvDate)
        val tvSpeed: TextView = itemView.findViewById(R.id.tvSpeedHistory)
        val tvCoords: TextView = itemView.findViewById(R.id.tvCoords)
        val tvAccel: TextView = itemView.findViewById(R.id.tvAccel)
    }
    
    fun updateData(newDataList: List<TrackingData>) {
        dataList = newDataList
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val item = dataList[position]
        holder.tvDate.text = item.timestamp
        holder.tvSpeed.text = String.format(Locale.US, "%.1f км/год", item.speedKmh)
        holder.tvCoords.text = String.format(Locale.US, "%.4f, %.4f", item.latitude, item.longitude)
        holder.tvAccel.text = String.format(Locale.US, "Accel: %.2f", item.accelMagnitude)
    }

    override fun getItemCount() = dataList.size
}