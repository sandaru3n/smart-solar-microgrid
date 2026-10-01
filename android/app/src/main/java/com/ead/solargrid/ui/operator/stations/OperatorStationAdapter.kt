package com.ead.solargrid.ui.operator.stations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ead.solargrid.R
import com.ead.solargrid.databinding.ItemOperatorStationBinding
import com.ead.solargrid.models.SolarStation
import com.ead.solargrid.ui.operator.OperatorScreen
import com.ead.solargrid.ui.operator.OperatorTone

class OperatorStationAdapter(
    private val onClick: (SolarStation) -> Unit
) : ListAdapter<SolarStation, OperatorStationAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemOperatorStationBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val b: ItemOperatorStationBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(station: SolarStation) {
            val context = b.root.context
            OperatorScreen.applyTone(
                b.iconBadge, b.ivIcon, R.drawable.ic_op_station,
                if (station.isActive) OperatorTone.GREEN else OperatorTone.NEUTRAL
            )
            b.tvName.text = station.name
            b.tvAddress.text = station.address
            b.tvAddress.isVisible = !station.address.isNullOrBlank()

            val capacity = StationFormat.kw(context, station.capacityKw)
            val battery = StationFormat.batterySlots(context, station.batteryStorageSlots)
            OperatorScreen.applyChip(b.tvCapacity, capacity, OperatorTone.YELLOW)
            OperatorScreen.applyChip(b.tvBattery, battery, OperatorTone.BLUE)

            b.root.contentDescription = context.getString(
                R.string.op_station_item_description,
                station.name,
                station.address.orEmpty(),
                capacity,
                battery
            )
            b.root.setOnClickListener { onClick(station) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SolarStation>() {
        override fun areItemsTheSame(old: SolarStation, new: SolarStation) = old.id == new.id
        override fun areContentsTheSame(old: SolarStation, new: SolarStation) = old == new
    }
}
