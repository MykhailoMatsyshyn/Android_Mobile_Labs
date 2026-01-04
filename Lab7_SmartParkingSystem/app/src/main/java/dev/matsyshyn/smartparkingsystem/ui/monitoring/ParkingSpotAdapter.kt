package dev.matsyshyn.smartparkingsystem.ui.monitoring

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import dev.matsyshyn.smartparkingsystem.R

class ParkingSpotAdapter(
    private var spots: List<Int> = List(100) { 0 }
) : RecyclerView.Adapter<ParkingSpotAdapter.ParkingSpotViewHolder>() {

    inner class ParkingSpotViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val spotView: View = itemView
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ParkingSpotViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_parking_spot, parent, false)
        return ParkingSpotViewHolder(view)
    }

    override fun onBindViewHolder(holder: ParkingSpotViewHolder, position: Int) {
        val isOccupied = spots[position] == 1
        val backgroundRes = if (isOccupied) {
            R.drawable.parking_spot_occupied
        } else {
            R.drawable.parking_spot_background
        }
        holder.spotView.setBackgroundResource(backgroundRes)
    }

    override fun getItemCount(): Int = spots.size

    fun updateSpots(newSpots: List<Int>) {
        spots = newSpots
        notifyDataSetChanged()
    }
}





