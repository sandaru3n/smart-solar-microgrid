package com.ead.solargrid.ui.operator.reservations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ead.solargrid.R
import com.ead.solargrid.databinding.ItemOperatorListFooterBinding
import com.ead.solargrid.databinding.ItemOperatorReservationBinding
import com.ead.solargrid.models.ReservationItem
import com.ead.solargrid.qr.QrValidity
import com.ead.solargrid.ui.home.ReservationUi
import com.ead.solargrid.ui.operator.OperatorReservationStatus
import com.ead.solargrid.ui.operator.OperatorScreen
import com.ead.solargrid.ui.operator.OperatorTone
import java.util.Locale

sealed interface ReservationRow {
    data class Reservation(val item: ReservationItem, val busy: Boolean) : ReservationRow
    data class Footer(val failed: Boolean) : ReservationRow
}

class OperatorReservationAdapter(
    private val onApprove: (ReservationItem) -> Unit,
    private val onReject: (ReservationItem) -> Unit,
    private val onRetryMore: () -> Unit,
    /** Injected so "has the slot started" is checked against the same clock for every row. */
    private val now: () -> Long = System::currentTimeMillis
) : ListAdapter<ReservationRow, RecyclerView.ViewHolder>(Diff) {

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ReservationRow.Reservation -> TYPE_RESERVATION
        is ReservationRow.Footer -> TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_RESERVATION) {
            ReservationHolder(ItemOperatorReservationBinding.inflate(inflater, parent, false))
        } else {
            FooterHolder(ItemOperatorListFooterBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is ReservationRow.Reservation -> (holder as ReservationHolder).bind(row)
            is ReservationRow.Footer -> (holder as FooterHolder).bind(row)
        }
    }

    private inner class ReservationHolder(private val b: ItemOperatorReservationBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(row: ReservationRow.Reservation) {
            val item = row.item
            val context = b.root.context
            val status = OperatorReservationStatus.from(item.status)
            val tone = status?.tone ?: OperatorTone.NEUTRAL

            OperatorScreen.applyTone(b.iconBadge, b.ivIcon, R.drawable.ic_op_station, tone)
            b.tvStation.text = item.stationName?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.op_res_unknown_station)
            b.tvSlot.text = ReservationUi.formatSlotRange(item.slotStartTimeUtc, item.slotEndTimeUtc)
            OperatorScreen.applyChip(b.tvStatus, status?.let { context.getString(it.label) } ?: item.status, tone)
            b.tvProsumer.text = context.getString(R.string.confirm_prosumer_nic_only, item.prosumerId)
            b.tvReference.text = shortReference(item.id)

            val isPending = status == OperatorReservationStatus.PENDING
            val slotStarted = QrValidity.parseUtc(item.slotStartTimeUtc)?.toEpochMilli()?.let { it <= now() } == true
            val canApprove = isPending && !slotStarted

            val note = when {
                isPending && slotStarted -> context.getString(R.string.op_res_slot_started)
                else -> QrValidity.parseUtc(item.createdAtUtc)?.let {
                    context.getString(R.string.op_res_booked_at, ReservationUi.formatDateTime(it))
                }
            }
            b.tvNote.text = note
            b.tvNote.isVisible = note != null
            b.tvNote.setTextColor(
                context.getColor(if (isPending && slotStarted) R.color.op_amber_fg else R.color.op_text_secondary)
            )

            b.actions.isVisible = isPending
            b.btnApprove.isEnabled = canApprove && !row.busy
            b.btnReject.isEnabled = !row.busy
            b.btnApprove.setText(if (row.busy) R.string.op_res_working else R.string.op_res_approve)
            b.btnApprove.setOnClickListener { onApprove(item) }
            b.btnReject.setOnClickListener { onReject(item) }

            b.details.contentDescription = context.getString(
                R.string.op_res_item_description,
                b.tvStation.text,
                b.tvSlot.text,
                b.tvStatus.text,
                item.prosumerId,
                b.tvReference.text
            )
            b.btnApprove.contentDescription = context.getString(R.string.op_res_approve_description, b.tvStation.text)
            b.btnReject.contentDescription = context.getString(R.string.op_res_reject_description, b.tvStation.text)
        }
    }

    private inner class FooterHolder(private val b: ItemOperatorListFooterBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(row: ReservationRow.Footer) {
            b.progress.isVisible = !row.failed
            b.btnRetry.isVisible = row.failed
            b.btnRetry.setOnClickListener { onRetryMore() }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ReservationRow>() {
        override fun areItemsTheSame(old: ReservationRow, new: ReservationRow) = when {
            old is ReservationRow.Reservation && new is ReservationRow.Reservation -> old.item.id == new.item.id
            else -> old is ReservationRow.Footer && new is ReservationRow.Footer
        }

        override fun areContentsTheSame(old: ReservationRow, new: ReservationRow) = old == new
    }

    companion object {
        private const val TYPE_RESERVATION = 0
        private const val TYPE_FOOTER = 1

        /** Same booking reference the web desk shows: the last 8 characters of the id. */
        fun shortReference(id: String) = "…" + id.takeLast(8).uppercase(Locale.ROOT)
    }
}
