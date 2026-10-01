package com.ead.solargrid.ui.operator.stations

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.ead.solargrid.R
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.databinding.ActivityOperatorStationDetailBinding
import com.ead.solargrid.databinding.ItemOperatorScheduleRowBinding
import com.ead.solargrid.databinding.ItemOperatorSlotRowBinding
import com.ead.solargrid.databinding.ItemOperatorStatBinding
import com.ead.solargrid.models.EnergyBookingSlotDto
import com.ead.solargrid.models.SolarStation
import com.ead.solargrid.models.StationSchedule
import com.ead.solargrid.qr.QrValidity
import com.ead.solargrid.ui.operator.OperatorReservationStatus
import com.ead.solargrid.ui.operator.OperatorScreen
import com.ead.solargrid.ui.operator.OperatorTone
import com.ead.solargrid.ui.operator.reservations.OperatorReservationsActivity
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Grid Operator: a station's details, weekly hours and how full its booked slots are. */
class OperatorStationDetailActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_STATION_ID = "station_id"
        private const val EXTRA_STATION_NAME = "station_name"

        /** Today plus the next six days, like the prosumer booking flow. */
        private const val DAY_COUNT = 7L

        fun newIntent(context: Context, stationId: String, stationName: String?): Intent =
            Intent(context, OperatorStationDetailActivity::class.java)
                .putExtra(EXTRA_STATION_ID, stationId)
                .putExtra(EXTRA_STATION_NAME, stationName)
    }

    private lateinit var binding: ActivityOperatorStationDetailBinding
    private lateinit var viewModel: OperatorStationDetailViewModel
    private lateinit var stationId: String

    private val countFormat = NumberFormat.getIntegerInstance()
    private val dayChipDates = mutableMapOf<Int, LocalDate>()

    /** Approving or rejecting changes the pending count and slot fill, so refresh on the way back. */
    private val openReservations = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && ::viewModel.isInitialized) viewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_STATION_ID)
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        stationId = id

        if (SessionManager(this).fetchAuthToken().isNullOrEmpty()) {
            OperatorScreen.sessionExpired(this)
            return
        }

        binding = ActivityOperatorStationDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[OperatorStationDetailViewModel::class.java]

        OperatorScreen.applyInsets(this, binding.root, binding.topBar, binding.scroll)
        binding.tvTitle.text = intent.getStringExtra(EXTRA_STATION_NAME) ?: getString(R.string.operator_action_stations)
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }

        binding.swipeRefresh.setColorSchemeResources(R.color.op_primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.op_surface)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        setUpStats()
        setUpDayChips()

        viewModel.state.observe(this, ::render)
        viewModel.init(stationId)
    }

    // ----- Setup -----

    private fun setUpStats() {
        binding.cardCapacity.setUp(R.drawable.ic_op_sun, R.string.operator_stat_output, OperatorTone.YELLOW)
        binding.cardBattery.setUp(R.drawable.ic_op_battery, R.string.operator_stat_battery, OperatorTone.BLUE)
        binding.cardPending.setUp(R.drawable.ic_op_schedule, R.string.op_station_stat_pending, OperatorTone.AMBER)
        binding.cardPending.root.isClickable = true
        binding.cardPending.root.isFocusable = true
        binding.cardPending.root.setOnClickListener {
            openReservations.launch(
                OperatorReservationsActivity.allIntent(
                    this,
                    status = OperatorReservationStatus.PENDING,
                    stationId = stationId,
                    stationName = currentStationName()
                )
            )
        }
        binding.btnReservations.setOnClickListener {
            openReservations.launch(
                OperatorReservationsActivity.allIntent(this, stationId = stationId, stationName = currentStationName())
            )
        }
    }

    private fun ItemOperatorStatBinding.setUp(@DrawableRes icon: Int, @StringRes label: Int, tone: OperatorTone) {
        OperatorScreen.applyTone(iconBadge, ivIcon, icon, tone)
        tvLabel.setText(label)
        tvValue.textSize = 20f
    }

    private fun setUpDayChips() {
        val today = LocalDate.now(StationFormat.ZONE)
        val dayFormat = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
        (0 until DAY_COUNT).map { today.plusDays(it) }.forEachIndexed { index, day ->
            val chip = layoutInflater.inflate(R.layout.item_operator_filter_chip, binding.dayChips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = when (index) {
                0 -> getString(R.string.op_station_today)
                1 -> getString(R.string.op_station_tomorrow)
                else -> day.format(dayFormat)
            }
            dayChipDates[chip.id] = day
            binding.dayChips.addView(chip)
        }
        binding.dayChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val day = checkedIds.firstOrNull()?.let(dayChipDates::get) ?: return@setOnCheckedStateChangeListener
            viewModel.selectDay(day)
        }
    }

    // ----- Rendering -----

    private fun render(state: StationDetailState) {
        if (state.sessionExpired) {
            OperatorScreen.sessionExpired(this)
            return
        }

        binding.swipeRefresh.isRefreshing = state.isLoading
        binding.btnRefresh.isEnabled = !state.isLoading
        binding.errorCard.isVisible = state.errorMessage != null && !state.isLoading
        binding.tvError.text = state.errorMessage

        state.station?.let(::renderStation)
        binding.cardPending.showValue(state.pendingCount?.let(countFormat::format))
        renderSchedule(state.schedules)
        renderDayChips(state.selectedDay)
        renderSlots(state)
    }

    private fun renderStation(station: SolarStation) {
        binding.tvTitle.text = station.name
        binding.tvName.text = station.name
        binding.tvAddress.text = station.address
        binding.tvAddress.isVisible = !station.address.isNullOrBlank()
        val tone = if (station.isActive) OperatorTone.GREEN else OperatorTone.NEUTRAL
        OperatorScreen.applyTone(binding.iconBadge, binding.ivIcon, R.drawable.ic_op_station, tone)
        OperatorScreen.applyChip(
            binding.tvActive,
            getString(if (station.isActive) R.string.op_station_active else R.string.op_station_inactive),
            tone
        )

        binding.cardCapacity.showValue(StationFormat.kw(this, station.capacityKw))
        binding.cardBattery.showValue(countFormat.format(station.batteryStorageSlots.toLong()))

        val hasLocation = station.latitude != 0.0 || station.longitude != 0.0
        binding.btnDirections.isEnabled = hasLocation
        binding.btnDirections.setOnClickListener { openDirections(station) }
    }

    private fun ItemOperatorStatBinding.showValue(value: String?) {
        tvValue.text = value ?: getString(R.string.operator_value_placeholder)
        root.contentDescription = getString(
            R.string.operator_card_description,
            tvLabel.text,
            value ?: getString(R.string.operator_updated_never)
        )
    }

    private fun renderSchedule(schedules: List<StationSchedule>?) {
        val rows = binding.scheduleRows
        rows.removeAllViews()
        val today = LocalDate.now(StationFormat.ZONE).dayOfWeek

        StationFormat.WEEK.forEach { day ->
            val row = ItemOperatorScheduleRowBinding.inflate(layoutInflater, rows, true)
            val schedule = schedules?.firstOrNull { it.day.equals(day.name, ignoreCase = true) }
            val isToday = day == today

            row.tvDay.text = day.getDisplayName(TextStyle.FULL, Locale.getDefault())
            row.tvToday.isVisible = isToday
            if (isToday) OperatorScreen.applyChip(row.tvToday, getString(R.string.op_station_today), OperatorTone.AMBER)
            row.tvDay.setTypeface(null, if (isToday) Typeface.BOLD else Typeface.NORMAL)

            val open = schedule?.isAvailable == true
            row.tvHours.text = when {
                schedules == null -> getString(R.string.operator_value_placeholder)
                schedule == null -> getString(R.string.op_station_hours_not_set)
                !open -> getString(R.string.op_station_closed)
                else -> getString(
                    R.string.op_station_hours,
                    StationFormat.clock(schedule.openingTime),
                    StationFormat.clock(schedule.closingTime)
                )
            }
            row.tvHours.setTextColor(
                ContextCompat.getColor(this, if (open) R.color.op_text else R.color.op_text_secondary)
            )
            row.tvHours.setTypeface(null, if (isToday) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun renderDayChips(selected: LocalDate) {
        val chipId = dayChipDates.entries.firstOrNull { it.value == selected }?.key ?: return
        if (binding.dayChips.checkedChipId != chipId) binding.dayChips.check(chipId)
    }

    private fun renderSlots(state: StationDetailState) {
        val slots = state.slots
        val loading = slots == null && state.slotsError == null
        binding.slotsProgress.isInvisible = !loading

        binding.slotRows.removeAllViews()
        val message = when {
            state.slotsError != null -> state.slotsError
            slots != null && slots.isEmpty() -> getString(R.string.op_station_slots_empty)
            else -> null
        }
        binding.tvSlotsMessage.isVisible = message != null
        binding.tvSlotsMessage.text = message

        val now = System.currentTimeMillis()
        slots.orEmpty().forEach { slot -> addSlotRow(slot, now) }
    }

    private fun addSlotRow(slot: EnergyBookingSlotDto, now: Long) {
        val row = ItemOperatorSlotRowBinding.inflate(layoutInflater, binding.slotRows, true)
        val start = QrValidity.parseUtc(slot.startTimeUtc)
        val end = QrValidity.parseUtc(slot.endTimeUtc)
        val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
        row.tvTime.text = if (start != null && end != null) {
            getString(
                R.string.op_station_hours,
                start.atZone(StationFormat.ZONE).format(clock),
                end.atZone(StationFormat.ZONE).format(clock)
            )
        } else {
            slot.startTimeUtc
        }

        val max = slot.maximumBookings.coerceAtLeast(0)
        val booked = slot.reservedBookings.coerceAtLeast(0)
        val ended = end != null && end.toEpochMilli() <= now
        val full = max > 0 && booked >= max
        val (label, tone) = when {
            ended -> getString(R.string.op_station_slot_ended) to OperatorTone.NEUTRAL
            full -> getString(R.string.op_station_slot_full) to OperatorTone.RED
            else -> getString(R.string.op_station_slot_left, slot.remainingBookings) to OperatorTone.GREEN
        }
        OperatorScreen.applyChip(row.tvState, label, tone)

        row.fill.progress = if (max > 0) (booked * 100 / max).coerceIn(0, 100) else 0
        row.fill.setIndicatorColor(ContextCompat.getColor(this, if (full) R.color.op_red_fg else R.color.op_primary))
        row.fill.trackColor = ContextCompat.getColor(this, R.color.op_neutral_bg)
        row.tvBooked.text = getString(R.string.op_station_slot_booked, booked, max)
        row.root.contentDescription = "${row.tvTime.text}. ${row.tvBooked.text}. $label"
    }

    // ----- Actions -----

    private fun currentStationName(): String? =
        viewModel.state.value?.station?.name ?: intent.getStringExtra(EXTRA_STATION_NAME)

    private fun openDirections(station: SolarStation) {
        val label = Uri.encode(station.name)
        val uri = Uri.parse("geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}($label)")
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Snackbar.make(binding.root, R.string.op_station_no_maps, Snackbar.LENGTH_LONG).show()
        }
    }
}
