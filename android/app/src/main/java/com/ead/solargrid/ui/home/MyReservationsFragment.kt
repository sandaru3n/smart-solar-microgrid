package com.ead.solargrid.ui.home

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import androidx.core.content.ContextCompat
import org.json.JSONObject
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.R
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.databinding.DialogBookingActionsBinding
import com.ead.solargrid.databinding.DialogBookingCancelBinding
import com.ead.solargrid.databinding.DialogBookingFilterBinding
import com.ead.solargrid.databinding.DialogBookingMessageBinding
import com.ead.solargrid.databinding.FragmentProsumerBookingsBinding
import com.ead.solargrid.databinding.ItemBookingSlotRowBinding
import com.ead.solargrid.databinding.ItemBookingStationCardBinding
import com.ead.solargrid.models.CancelReservationRequest
import com.ead.solargrid.models.CreateReservationRequest
import com.ead.solargrid.models.UpdateReservationRequest
import com.ead.solargrid.models.EnergyBookingSlotDto
import com.ead.solargrid.models.ReservationItem
import com.ead.solargrid.models.SolarStation
import com.ead.solargrid.ui.home.booking.BookingRules
import com.ead.solargrid.ui.home.booking.StationSlotBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MyReservationsFragment : Fragment() {

    private var _binding: FragmentProsumerBookingsBinding? = null
    private val binding get() = _binding!!

    private enum class Step { LIST, STATION, SLOT, SUMMARY }

    private var step = Step.LIST
    private var stations: List<SolarStation> = emptyList()
    private enum class BookingListFilter { BOTH, PENDING, APPROVED }

    private var pendingItems: List<ReservationItem> = emptyList()
    private var listFilter = BookingListFilter.BOTH
    private var bookingQuery = ""
    private var selectedStation: SolarStation? = null
    private var loadedSlots: List<EnergyBookingSlotDto> = emptyList()
    private var selectedSlot: EnergyBookingSlotDto? = null
    private var selectedDayKey: String? = null
    private var submitting = false
    private var editingReservation: ReservationItem? = null
    private var stationsFetchJob: Job? = null
    private var stationSearchDebounceJob: Job? = null
    private val slotsByStationId = mutableMapOf<String, List<EnergyBookingSlotDto>>()
    private var slotsFetchJob: Job? = null
    private var slotsLoadingStationId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProsumerBookingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupStepHeaders()

        binding.btnCreateBooking.setOnClickListener { startCreateBooking() }
        binding.btnPendingFilter.setOnClickListener { showBookingFilter() }
        binding.etBookingSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                bookingQuery = s?.toString().orEmpty()
                if (step == Step.LIST && pendingItems.isNotEmpty()) renderPendingBookings()
            }
        })
        binding.btnBookingBack.setOnClickListener { onBackPressed() }
        binding.btnBookingContinue.setOnClickListener { onContinue() }
        binding.btnConfirmBooking.setOnClickListener { confirmBooking() }
        binding.btnSummaryErrorAction.setOnClickListener { onSummaryErrorAction() }

        binding.etStationSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                stationSearchDebounceJob?.cancel()
                stationSearchDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(200)
                    updateStationCountLabel()
                    renderStationCards()
                }
            }
        })
        binding.etStationSearch.setOnFocusChangeListener { _, hasFocus ->
            applyStationSearchStroke(hasFocus)
        }

        showStep(Step.LIST)
        prefetchStationsQuietly()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            onBookingsTabSelected()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden && step == Step.LIST) {
            loadPendingBookings()
        }
    }

    private fun onBookingsTabSelected() {
        if ((activity as? ProsumerHomeActivity)?.consumePendingNewBooking() == true) {
            startCreateBooking()
            return
        }
        if (step == Step.LIST) {
            loadPendingBookings()
            prefetchStationsQuietly()
        }
    }

    private fun startCreateBooking() {
        editingReservation = null
        selectedStation = null
        selectedSlot = null
        selectedDayKey = null
        binding.etStationSearch.text?.clear()
        binding.btnBookingContinue.isEnabled = false
        goToStep(Step.STATION)
    }

    private fun startEditBooking(item: ReservationItem) {
        editingReservation = item
        selectedSlot = null
        selectedDayKey = null
        selectedStation = stations.find { it.id == item.stationId }
        binding.etStationSearch.text?.clear()
        binding.btnBookingContinue.isEnabled = selectedStation != null
        prefetchSlotsForStation(item.stationId)
        goToStep(Step.STATION)
    }

    private fun returnToBookingsList() {
        editingReservation = null
        selectedStation = null
        selectedSlot = null
        selectedDayKey = null
        binding.etStationSearch.text?.clear()
        binding.btnBookingContinue.isEnabled = false
        goToStep(Step.LIST)
    }

    private fun setupStepHeaders() {
        binding.headerStation.tvStepNumber.text = "1"
        binding.headerStation.tvStepTitle.setText(R.string.booking_available_stations)
        binding.headerSlot.tvStepNumber.text = "2"
        binding.headerSlot.tvStepTitle.setText(R.string.booking_energy_slots)

        binding.headerSummary.tvStepNumber.text = "3"
        binding.headerSummary.tvStepTitle.setText(R.string.booking_review_details)
        styleSummaryStepAsideBadge()
    }

    private fun styleSummaryStepAsideBadge() {
        val aside = binding.headerSummary.tvStepAside
        aside.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        val density = resources.displayMetrics.density
        val h = (10 * density).toInt()
        val v = (5 * density).toInt()
        aside.setPadding(h, v, h, v)
    }

    private fun onBackPressed() {
        when (step) {
            Step.STATION -> returnToBookingsList()
            Step.SLOT -> goToStep(Step.STATION)
            Step.SUMMARY -> goToStep(Step.SLOT)
            Step.LIST -> Unit
        }
    }

    private fun onContinue() {
        when (step) {
            Step.STATION -> {
                val station = selectedStation
                if (station == null) {
                    Toast.makeText(requireContext(), R.string.booking_pick_station, Toast.LENGTH_SHORT).show()
                    return
                }
                prefetchSlotsForStation(station.id)
                goToStep(Step.SLOT)
            }
            Step.SLOT -> {
                if (selectedSlot == null) {
                    Toast.makeText(requireContext(), R.string.booking_pick_slot, Toast.LENGTH_SHORT).show()
                    return
                }
                populateSummary()
                goToStep(Step.SUMMARY)
            }
            else -> Unit
        }
    }

    private fun goToStep(next: Step) {
        step = next
        showStep(next)
    }

    private fun showStep(current: Step) {
        val isList = current == Step.LIST
        binding.stepList.isVisible = isList
        binding.stepStation.isVisible = current == Step.STATION
        binding.stepSlot.isVisible = current == Step.SLOT
        binding.stepSummary.isVisible = current == Step.SUMMARY

        binding.wizardToolbar.isVisible = !isList
        binding.btnBookingBack.isVisible = current != Step.LIST
        binding.btnBookingContinue.isVisible = current == Step.STATION || current == Step.SLOT
        binding.btnBookingContinue.isEnabled = when (current) {
            Step.STATION -> selectedStation != null
            Step.SLOT -> selectedSlot != null
            else -> false
        }

        if (current != Step.STATION) {
            binding.etStationSearch.clearFocus()
            applyStationSearchStroke(false)
        }

        when (current) {
            Step.LIST -> loadPendingBookings()
            Step.STATION -> {
                binding.tvBookingEyebrow.isVisible = true
                binding.tvBookingEyebrow.setText(
                    if (editingReservation != null) R.string.booking_eyebrow_edit
                    else R.string.booking_eyebrow_new
                )
                binding.tvBookingTitle.setText(R.string.booking_step_station)
                binding.tvBookingSubtitle.isVisible = false
                applyStationSearchStroke(binding.etStationSearch.hasFocus())
                presentStationStep()
            }
            Step.SLOT -> {
                selectedDayKey = null
                selectedSlot = null
                binding.btnBookingContinue.isEnabled = false
                binding.tvBookingEyebrow.isVisible = true
                binding.tvBookingTitle.setText(R.string.booking_step_slot)
                binding.tvBookingSubtitle.isVisible = true
                binding.tvBookingSubtitle.text = selectedStation?.name ?: ""
                binding.tvSlotMonth.text = monthLabel()
                presentSlotStep()
            }
            Step.SUMMARY -> {
                binding.tvBookingEyebrow.isVisible = true
                binding.tvBookingEyebrow.setText(
                    if (editingReservation != null) R.string.booking_eyebrow_edit
                    else R.string.booking_eyebrow_new
                )
                binding.tvBookingTitle.setText(
                    if (editingReservation != null) R.string.booking_edit_title
                    else R.string.booking_summary_title
                )
                binding.tvBookingSubtitle.isVisible = false
                binding.headerSummary.tvStepAside.setText(R.string.booking_summary_eyebrow)
                binding.btnConfirmBooking.setText(
                    if (editingReservation != null) R.string.booking_update
                    else R.string.booking_confirm
                )
                populateSummary()
            }
        }
    }

    private fun showRoundedDialog(content: android.view.View): Dialog {
        val dialog = Dialog(requireContext())
        dialog.setContentView(content)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
        return dialog
    }

    private fun showBookingFilter() {
        val dialogBinding = DialogBookingFilterBinding.inflate(layoutInflater)
        val dialog = showRoundedDialog(dialogBinding.root)
        var selected = listFilter
        fun paint() {
            val density = resources.displayMetrics.density
            listOf(
                dialogBinding.optionBoth to BookingListFilter.BOTH,
                dialogBinding.optionPending to BookingListFilter.PENDING,
                dialogBinding.optionApproved to BookingListFilter.APPROVED
            ).forEach { (card, filter) ->
                val on = filter == selected
                card.strokeWidth = ((if (on) 2.5f else 1f) * density).toInt()
                card.strokeColor = ContextCompat.getColor(
                    requireContext(),
                    if (on) R.color.booking_card_selected_stroke else R.color.booking_search_stroke
                )
            }
        }
        paint()
        dialogBinding.optionBoth.setOnClickListener { selected = BookingListFilter.BOTH; paint() }
        dialogBinding.optionPending.setOnClickListener { selected = BookingListFilter.PENDING; paint() }
        dialogBinding.optionApproved.setOnClickListener { selected = BookingListFilter.APPROVED; paint() }
        dialogBinding.btnApplyFilter.setOnClickListener {
            listFilter = selected
            renderPendingBookings()
            dialog.dismiss()
        }
    }

    private fun loadPendingBookings() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val pending = api.getMyReservations(status = "Pending", pageSize = 50).body()?.items.orEmpty()
                val approved = api.getMyReservations(status = "Approved", pageSize = 50).body()?.items.orEmpty()
                pendingItems = (pending + approved)
                    .distinctBy { it.id }
                    .filter { item ->
                        item.status.equals("Pending", ignoreCase = true) ||
                            item.status.equals("Approved", ignoreCase = true)
                    }
                if (_binding == null) return@launch
                renderPendingBookings()
            } catch (_: Exception) {
                if (_binding == null) return@launch
                binding.tvPendingEmpty.isVisible = true
                binding.tvPendingEmpty.text = getString(R.string.dashboard_load_error)
            }
        }
    }

    private fun visibleBookings(): List<ReservationItem> {
        val query = bookingQuery.trim()
        return pendingItems.filter { item ->
            val statusMatch = when (listFilter) {
                BookingListFilter.PENDING -> item.status.equals("Pending", ignoreCase = true)
                BookingListFilter.APPROVED -> item.status.equals("Approved", ignoreCase = true)
                BookingListFilter.BOTH -> true
            }
            statusMatch && matchesBookingQuery(item, query)
        }
    }

    private fun matchesBookingQuery(item: ReservationItem, query: String): Boolean {
        if (query.isEmpty()) return true
        val station = item.stationName ?: item.stationId
        val whenText = ReservationUi.formatSlotRange(item.slotStartTimeUtc, item.slotEndTimeUtc)
        val reference = item.id.takeLast(8)
        return station.contains(query, ignoreCase = true) ||
            item.status.contains(query, ignoreCase = true) ||
            item.id.contains(query, ignoreCase = true) ||
            reference.contains(query, ignoreCase = true) ||
            whenText.contains(query, ignoreCase = true)
    }

    private fun renderPendingBookings() {
        val userName = SessionManager(requireContext()).getUserSession()?.name
        val sorted = visibleBookings().sortedByDescending { item ->
            BookingRules.parseInstant(item.slotStartTimeUtc)?.toEpochMilli() ?: 0L
        }
        binding.tvPendingCount.text = sorted.size.toString()
        binding.tvBookingsHeading.setText(
            when (listFilter) {
                BookingListFilter.PENDING -> R.string.bookings_filter_pending
                BookingListFilter.APPROVED -> R.string.bookings_filter_approved
                BookingListFilter.BOTH -> R.string.bookings_pending_heading
            }
        )
        binding.pendingList.removeAllViews()
        if (sorted.isEmpty()) {
            binding.tvPendingEmpty.isVisible = true
            binding.tvPendingEmpty.setText(
                if (bookingQuery.isNotBlank()) {
                    R.string.bookings_search_empty
                } else {
                    when (listFilter) {
                        BookingListFilter.PENDING -> R.string.bookings_pending_empty
                        BookingListFilter.APPROVED -> R.string.bookings_approved_empty
                        BookingListFilter.BOTH -> R.string.bookings_active_empty
                    }
                }
            )
        } else {
            binding.tvPendingEmpty.isVisible = false
            sorted.forEach { item ->
                ReservationUi.addPendingBookingCard(
                    binding.pendingList,
                    layoutInflater,
                    item,
                    userName
                ) { openBookingActions(it) }
            }
        }
    }

    private fun openBookingActions(item: ReservationItem) {
        val blocked = BookingRules.changeBlockedReason(item.status, item.slotStartTimeUtc)
        if (blocked != null) {
            val messageBinding = DialogBookingMessageBinding.inflate(layoutInflater)
            val dialog = showRoundedDialog(messageBinding.root)
            messageBinding.tvMessageTitle.text = item.stationName ?: getString(R.string.booking_actions_title)
            messageBinding.tvMessageBody.text = blocked
            messageBinding.btnMessageOk.setOnClickListener { dialog.dismiss() }
            return
        }

        val actionsBinding = DialogBookingActionsBinding.inflate(layoutInflater)
        val dialog = showRoundedDialog(actionsBinding.root)
        actionsBinding.tvActionTitle.text = item.stationName ?: getString(R.string.booking_actions_title)
        actionsBinding.tvActionWhen.text = ReservationUi.formatSlotRange(item.slotStartTimeUtc, item.slotEndTimeUtc)
        actionsBinding.btnEditBooking.setOnClickListener {
            dialog.dismiss()
            startEditBooking(item)
        }
        actionsBinding.btnCancelBooking.setOnClickListener {
            dialog.dismiss()
            confirmCancelBooking(item)
        }
        actionsBinding.btnCloseActions.setOnClickListener { dialog.dismiss() }
    }

    private fun confirmCancelBooking(item: ReservationItem) {
        val cancelBinding = DialogBookingCancelBinding.inflate(layoutInflater)
        val dialog = showRoundedDialog(cancelBinding.root)
        cancelBinding.btnKeepBooking.setOnClickListener { dialog.dismiss() }
        cancelBinding.btnConfirmCancel.setOnClickListener {
            dialog.dismiss()
            cancelBooking(item)
        }
    }

    private fun cancelBooking(item: ReservationItem) {
        val blocked = BookingRules.changeBlockedReason(item.status, item.slotStartTimeUtc)
        if (blocked != null) {
            Toast.makeText(requireContext(), blocked, Toast.LENGTH_LONG).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val response = api.cancelReservation(item.id, CancelReservationRequest())
                if (_binding == null) return@launch
                if (response.isSuccessful) {
                    slotsByStationId.remove(item.stationId)
                    Toast.makeText(requireContext(), R.string.booking_cancelled, Toast.LENGTH_LONG).show()
                    loadPendingBookings()
                } else {
                    Toast.makeText(
                        requireContext(),
                        parseApiErrorMessage(response.errorBody()?.string()),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                Toast.makeText(
                    requireContext(),
                    e.message ?: getString(R.string.booking_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun filteredStations(): List<SolarStation> {
        val query = binding.etStationSearch.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        if (query.isEmpty()) return stations
        return stations.filter { station ->
            station.name.lowercase(Locale.getDefault()).contains(query) ||
                station.address?.lowercase(Locale.getDefault())?.contains(query) == true
        }
    }

    private fun updateStationCountLabel() {
        binding.headerStation.tvStepAside.text =
            getString(R.string.booking_active_stations_count, filteredStations().size)
    }

    /** Show cached stations instantly; only hit the network when the cache is empty. */
    private fun presentStationStep() {
        binding.tvStationsError.isVisible = false
        if (stations.isNotEmpty()) {
            binding.progressStations.isVisible = false
            binding.stationList.isVisible = true
            updateStationCountLabel()
            renderStationCards()
            return
        }
        if (stationsFetchJob?.isActive == true) {
            binding.progressStations.isVisible = true
            binding.stationList.isVisible = false
            return
        }
        fetchStations(showBlockingLoader = true)
    }

    /** Load stations in the background while the user is on the bookings list. */
    private fun prefetchStationsQuietly() {
        if (stations.isNotEmpty() || stationsFetchJob?.isActive == true) return
        fetchStations(showBlockingLoader = false)
    }

    private fun fetchStations(showBlockingLoader: Boolean) {
        if (showBlockingLoader && _binding != null) {
            binding.progressStations.isVisible = true
            binding.stationList.isVisible = stations.isNotEmpty()
            binding.tvStationsError.isVisible = false
            if (stations.isEmpty()) {
                binding.headerStation.tvStepAside.text = ""
            }
        }

        stationsFetchJob?.cancel()
        stationsFetchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val response = api.getStations()
                if (_binding == null) return@launch
                binding.progressStations.isVisible = false
                if (!response.isSuccessful) {
                    if (step == Step.STATION && stations.isEmpty()) {
                        binding.tvStationsError.isVisible = true
                        binding.tvStationsError.text = getString(R.string.dashboard_load_error)
                    }
                    return@launch
                }
                stations = response.body().orEmpty().filter { it.isActive }
                val editing = editingReservation
                if (editing != null && selectedStation == null) {
                    selectedStation = stations.find { it.id == editing.stationId }
                    if (_binding != null && step == Step.STATION) {
                        binding.btnBookingContinue.isEnabled = selectedStation != null
                    }
                }
                if (step == Step.STATION) {
                    updateStationCountLabel()
                    renderStationCards()
                }
            } catch (_: Exception) {
                if (_binding == null) return@launch
                binding.progressStations.isVisible = false
                if (step == Step.STATION && stations.isEmpty()) {
                    binding.tvStationsError.isVisible = true
                    binding.tvStationsError.text = getString(R.string.dashboard_load_error)
                }
            }
        }
    }

    private fun renderStationCards() {
        val visible = filteredStations()
        binding.stationList.removeAllViews()
        binding.tvStationsError.isVisible = visible.isEmpty() && stations.isNotEmpty()
        if (visible.isEmpty() && stations.isNotEmpty()) {
            binding.tvStationsError.text = getString(R.string.booking_no_station_match)
        }
        binding.stationList.isVisible = visible.isNotEmpty()
        val inflater = layoutInflater
        visible.forEach { station ->
            val cardBinding = ItemBookingStationCardBinding.inflate(inflater, binding.stationList, false)
            val selected = selectedStation?.id == station.id
            cardBinding.tvStationName.text = station.name
            cardBinding.tvStationAddress.text = station.address ?: "—"
            cardBinding.tvStationCapacity.text = getString(R.string.station_capacity_value, station.capacityKw)
            applyStationCardSelection(cardBinding, selected)
            cardBinding.root.tag = station.id
            cardBinding.root.setOnClickListener {
                selectedStation = station
                selectedSlot = null
                refreshStationCardSelection()
                binding.btnBookingContinue.isEnabled = true
                prefetchSlotsForStation(station.id)
            }
            binding.stationList.addView(cardBinding.root)
        }
    }

    private fun refreshStationCardSelection() {
        for (i in 0 until binding.stationList.childCount) {
            val root = binding.stationList.getChildAt(i)
            val stationId = root.tag as? String ?: continue
            val selected = selectedStation?.id == stationId
            applyStationCardSelection(ItemBookingStationCardBinding.bind(root), selected)
        }
    }

    private fun applyStationSearchStroke(focused: Boolean) {
        if (_binding == null) return
        val density = resources.displayMetrics.density
        val card = binding.stationSearchLayout
        if (focused) {
            card.strokeWidth = (2.5f * density).toInt()
            card.strokeColor = ContextCompat.getColor(requireContext(), R.color.booking_card_selected_stroke)
            card.cardElevation = 2f
        } else {
            card.strokeWidth = (1f * density).toInt()
            card.strokeColor = ContextCompat.getColor(requireContext(), R.color.booking_search_stroke)
            card.cardElevation = 1f
        }
    }

    private fun applyStationCardSelection(
        cardBinding: ItemBookingStationCardBinding,
        selected: Boolean
    ) {
        cardBinding.radioInner.isVisible = selected
        cardBinding.radioOuter.setBackgroundResource(
            if (selected) R.drawable.bg_station_radio_outer_selected
            else R.drawable.bg_station_radio_outer
        )
        val density = resources.displayMetrics.density
        val strokeDp = if (selected) 3.5f else 1f
        cardBinding.stationCardRoot.strokeWidth = (strokeDp * density).toInt()
        cardBinding.stationCardRoot.strokeColor = ContextCompat.getColor(
            requireContext(),
            if (selected) R.color.booking_card_selected_stroke else R.color.booking_search_stroke
        )
        cardBinding.stationCardRoot.cardElevation = if (selected) 8f else 2f
        cardBinding.stationCardRoot.translationZ = if (selected) 2f else 0f
    }

    private fun prefetchSlotsForStation(stationId: String) {
        if (slotsByStationId.containsKey(stationId)) return
        if (slotsLoadingStationId == stationId && slotsFetchJob?.isActive == true) return
        fetchSlotsForStation(stationId, showBlockingLoader = false)
    }

    private fun presentSlotStep() {
        val station = selectedStation ?: return
        binding.tvSlotsPlaceholder.isVisible = false
        val cached = slotsByStationId[station.id]
        if (cached != null) {
            loadedSlots = cached
            binding.progressSlots.isVisible = false
            buildDayChips()
            renderSlotsForSelectedDay()
            return
        }
        if (slotsLoadingStationId == station.id && slotsFetchJob?.isActive == true) {
            binding.progressSlots.isVisible = true
            binding.slotList.isVisible = loadedSlots.isNotEmpty()
            return
        }
        fetchSlotsForStation(station.id, showBlockingLoader = true)
    }

    private fun fetchSlotsForStation(stationId: String, showBlockingLoader: Boolean) {
        if (showBlockingLoader && _binding != null) {
            binding.progressSlots.isVisible = true
            binding.slotList.isVisible = loadedSlots.isNotEmpty()
            binding.tvSlotsPlaceholder.isVisible = false
            binding.headerSlot.tvStepAside.text = ""
        }

        slotsLoadingStationId = stationId
        slotsFetchJob?.cancel()
        slotsFetchJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val result = StationSlotBuilder.loadSelectableSlots(api, stationId)
                if (_binding == null) return@launch
                slotsByStationId[stationId] = result.slots
                if (step == Step.SLOT && selectedStation?.id == stationId) {
                    loadedSlots = result.slots
                    binding.progressSlots.isVisible = false
                    buildDayChips()
                    renderSlotsForSelectedDay()
                }
            } catch (_: Exception) {
                if (_binding == null) return@launch
                binding.progressSlots.isVisible = false
                if (step == Step.SLOT && selectedStation?.id == stationId && loadedSlots.isEmpty()) {
                    binding.tvSlotsPlaceholder.isVisible = true
                    binding.tvSlotsPlaceholder.text = getString(R.string.dashboard_load_error)
                }
            } finally {
                if (slotsLoadingStationId == stationId) {
                    slotsLoadingStationId = null
                }
            }
        }
    }

    private fun buildDayChips() {
        binding.dayChipRow.removeAllViews()
        val days = (0 until 8).map { LocalDate.now().plusDays(it.toLong()) }
        if (selectedDayKey == null) {
            selectedDayKey = firstOpenDayKey() ?: StationSlotBuilder.dayKey(days.first())
        }
        val activeKey = selectedDayKey!!
        val density = resources.displayMetrics.density
        days.forEach { day ->
            val key = StationSlotBuilder.dayKey(day)
            val selected = activeKey == key
            val chip = TextView(requireContext()).apply {
                text = day.format(DateTimeFormatter.ofPattern("EEE\nd", Locale.getDefault()))
                textAlignment = View.TEXT_ALIGNMENT_CENTER
                setPadding(
                    (14 * density).toInt(),
                    (10 * density).toInt(),
                    (14 * density).toInt(),
                    (10 * density).toInt()
                )
                setBackgroundResource(
                    if (selected) R.drawable.bg_booking_day_chip_selected
                    else R.drawable.bg_booking_day_chip
                )
                setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (selected) R.color.slate_900 else R.color.slate_600
                    )
                )
                setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
                textSize = 11f
                tag = key
                setOnClickListener {
                    if (selectedDayKey == key) return@setOnClickListener
                    selectedDayKey = key
                    selectedSlot = null
                    refreshDayChipSelection()
                    renderSlotsForSelectedDay()
                    binding.btnBookingContinue.isEnabled = false
                }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * density).toInt() }
            binding.dayChipRow.addView(chip, lp)
        }
    }

    private fun refreshDayChipSelection() {
        val key = selectedDayKey ?: return
        val density = resources.displayMetrics.density
        for (i in 0 until binding.dayChipRow.childCount) {
            val chip = binding.dayChipRow.getChildAt(i) as? TextView ?: continue
            val selected = chip.tag == key
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_booking_day_chip_selected
                else R.drawable.bg_booking_day_chip
            )
            chip.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (selected) R.color.slate_900 else R.color.slate_600
                )
            )
            chip.setTypeface(chip.typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    private fun updateSlotStepAside() {
        val dayKey = selectedDayKey ?: return
        val count = loadedSlots.count { StationSlotBuilder.dayKey(it.startTimeUtc) == dayKey }
        binding.headerSlot.tvStepAside.text = getString(R.string.booking_slots_on_day, count)
    }

    private fun firstOpenDayKey(): String? {
        val station = selectedStation ?: return null
        val now = System.currentTimeMillis()
        return loadedSlots.map { StationSlotBuilder.dayKey(it.startTimeUtc) }.distinct().firstOrNull { dayKey ->
            loadedSlots.any { slot ->
                StationSlotBuilder.dayKey(slot.startTimeUtc) == dayKey &&
                    BookingRules.slotAvailability(slot, station, now).bookable
            }
        }
    }

    private fun renderSlotsForSelectedDay() {
        val station = selectedStation ?: return
        val dayKey = selectedDayKey ?: return
        val now = System.currentTimeMillis()
        binding.slotList.removeAllViews()
        updateSlotStepAside()

        val entries = loadedSlots.filter { StationSlotBuilder.dayKey(it.startTimeUtc) == dayKey }
        if (entries.isEmpty()) {
            binding.slotList.isVisible = false
            binding.tvSlotsPlaceholder.isVisible = true
            binding.tvSlotsPlaceholder.setText(R.string.booking_no_slots_day)
            return
        }

        binding.tvSlotsPlaceholder.isVisible = false
        binding.slotList.isVisible = true
        val inflater = layoutInflater
        entries.forEach { slot ->
            val availability = BookingRules.slotAvailability(slot, station, now)
            val row = ItemBookingSlotRowBinding.inflate(inflater, binding.slotList, false)
            val slotKey = "${slot.id}|${slot.startTimeUtc}"
            val selected = selectedSlot?.id == slot.id &&
                selectedSlot?.startTimeUtc == slot.startTimeUtc
            row.tvSlotTime.text = ReservationUi.formatSlotRange(slot.startTimeUtc, slot.endTimeUtc)
            if (availability.bookable) {
                row.tvSlotAvailability.setText(R.string.booking_slot_available)
                row.tvSlotAvailability.setBackgroundResource(R.drawable.bg_slot_available_badge)
                row.tvSlotAvailability.setTextColor(0xFF065F46.toInt())
                row.tvSlotMeta.text = getString(R.string.booking_spaces_remaining, slot.remainingBookings)
            } else {
                row.tvSlotAvailability.text = getString(R.string.booking_unavailable)
                row.tvSlotAvailability.setBackgroundResource(R.drawable.bg_station_count_badge)
                row.tvSlotAvailability.setTextColor(ContextCompat.getColor(requireContext(), R.color.slate_600))
                row.tvSlotMeta.text = availability.reason ?: getString(R.string.booking_unavailable)
            }
            applySlotCardSelection(row, selected && availability.bookable)
            row.root.alpha = if (availability.bookable) 1f else 0.55f
            row.root.tag = slotKey
            row.root.isClickable = availability.bookable
            row.root.setOnClickListener {
                if (!availability.bookable) return@setOnClickListener
                selectedSlot = slot
                refreshSlotCardSelection()
                binding.btnBookingContinue.isEnabled = true
            }
            binding.slotList.addView(row.root)
        }
    }

    private fun applySlotCardSelection(row: ItemBookingSlotRowBinding, selected: Boolean) {
        row.radioInner.isVisible = selected
        row.radioOuter.setBackgroundResource(
            if (selected) R.drawable.bg_station_radio_outer_selected
            else R.drawable.bg_station_radio_outer
        )
        val density = resources.displayMetrics.density
        val strokeDp = if (selected) 3.5f else 1f
        row.slotCardRoot.strokeWidth = (strokeDp * density).toInt()
        row.slotCardRoot.strokeColor = ContextCompat.getColor(
            requireContext(),
            if (selected) R.color.booking_card_selected_stroke else R.color.booking_search_stroke
        )
        row.slotCardRoot.cardElevation = if (selected) 8f else 2f
    }

    private fun refreshSlotCardSelection() {
        val selected = selectedSlot ?: return
        val selectedKey = "${selected.id}|${selected.startTimeUtc}"
        for (i in 0 until binding.slotList.childCount) {
            val root = binding.slotList.getChildAt(i)
            val isSelected = root.tag == selectedKey
            applySlotCardSelection(ItemBookingSlotRowBinding.bind(root), isSelected)
        }
    }

    private fun populateSummary() {
        val user = SessionManager(requireContext()).getUserSession()
        val station = selectedStation
        val slot = selectedSlot

        binding.rowSummaryProsumer.tvSummaryLabel.setText(R.string.booking_label_prosumer)
        binding.rowSummaryProsumer.tvSummaryValue.text = user?.name ?: "—"

        binding.rowSummaryNic.tvSummaryLabel.setText(R.string.booking_label_nic)
        binding.rowSummaryNic.tvSummaryValue.text = user?.nic ?: "—"

        binding.rowSummaryStation.tvSummaryLabel.setText(R.string.booking_label_station)
        binding.rowSummaryStation.tvSummaryValue.text = station?.name ?: "—"
        binding.tvSummaryStationAddress.text = station?.address ?: "—"

        binding.rowSummaryDate.tvSummaryLabel.setText(R.string.booking_label_date)
        binding.rowSummaryDate.tvSummaryValue.text = formatLongDate(slot?.startTimeUtc)

        binding.rowSummaryTime.tvSummaryLabel.setText(R.string.booking_label_time)
        binding.rowSummaryTime.tvSummaryValue.text =
            ReservationUi.formatSlotRange(slot?.startTimeUtc, slot?.endTimeUtc)

        binding.rowSummaryAvailability.tvSummaryLabel.setText(R.string.booking_label_availability)
        binding.rowSummaryAvailability.tvSummaryValue.text =
            slot?.let { getString(R.string.booking_spaces_remaining, it.remainingBookings) } ?: "—"

        hideSummaryBookingError()
    }

    private enum class SummaryErrorAction { NONE, VIEW_BOOKINGS, CHANGE_SLOT }

    private var summaryErrorAction = SummaryErrorAction.NONE

    private fun hideSummaryBookingError() {
        if (_binding == null) return
        binding.summaryErrorCard.isVisible = false
        summaryErrorAction = SummaryErrorAction.NONE
    }

    private fun showSummaryBookingError(rawMessage: String?) {
        val message = parseApiErrorMessage(rawMessage)
        val lower = message.lowercase(Locale.getDefault())
        val isDuplicate = "already have an active reservation" in lower
        val isOverlap = "overlaps an existing" in lower
        val title = when {
            isDuplicate -> getString(R.string.booking_error_duplicate_title)
            isOverlap -> getString(R.string.booking_error_overlap_title)
            else -> getString(R.string.booking_error_generic_title)
        }
        binding.tvSummaryErrorTitle.text = title
        binding.tvSummaryErrorBody.text = message
        summaryErrorAction = when {
            isDuplicate -> SummaryErrorAction.VIEW_BOOKINGS
            isOverlap -> SummaryErrorAction.CHANGE_SLOT
            else -> SummaryErrorAction.NONE
        }
        if (summaryErrorAction != SummaryErrorAction.NONE) {
            binding.btnSummaryErrorAction.isVisible = true
            binding.btnSummaryErrorAction.text = when (summaryErrorAction) {
                SummaryErrorAction.VIEW_BOOKINGS -> getString(R.string.booking_error_view_bookings)
                SummaryErrorAction.CHANGE_SLOT -> getString(R.string.booking_error_change_slot)
                else -> ""
            }
        } else {
            binding.btnSummaryErrorAction.isVisible = false
        }
        binding.summaryErrorCard.isVisible = true
        binding.summaryErrorCard.post {
            binding.stepSummary.smoothScrollTo(0, binding.summaryErrorCard.top)
        }
    }

    private fun onSummaryErrorAction() {
        when (summaryErrorAction) {
            SummaryErrorAction.VIEW_BOOKINGS -> returnToBookingsList()
            SummaryErrorAction.CHANGE_SLOT -> {
                hideSummaryBookingError()
                goToStep(Step.SLOT)
            }
            SummaryErrorAction.NONE -> Unit
        }
    }

    private fun parseApiErrorMessage(raw: String?): String {
        if (raw.isNullOrBlank()) return getString(R.string.booking_failed)
        val trimmed = raw.trim()
        return try {
            JSONObject(trimmed).optString("message").takeIf { it.isNotBlank() } ?: trimmed
        } catch (_: Exception) {
            trimmed.removePrefix("\"").removeSuffix("\"")
        }
    }

    private fun confirmBooking() {
        if (submitting) return
        val station = selectedStation ?: return
        val slot = selectedSlot ?: return

        submitting = true
        binding.btnConfirmBooking.isEnabled = false
        hideSummaryBookingError()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val slotId = StationSlotBuilder.ensureStoredSlotId(api, station.id, slot)
                val editing = editingReservation
                val response = if (editing != null) {
                    api.updateReservation(
                        editing.id,
                        UpdateReservationRequest(slotId = slotId, stationId = station.id)
                    )
                } else {
                    api.createReservation(
                        CreateReservationRequest(slotId = slotId, stationId = station.id)
                    )
                }
                if (_binding == null) return@launch
                submitting = false
                binding.btnConfirmBooking.isEnabled = true
                if (response.isSuccessful) {
                    slotsByStationId.remove(station.id)
                    editing?.stationId?.let { slotsByStationId.remove(it) }
                    Toast.makeText(
                        requireContext(),
                        response.body()?.message ?: getString(
                            if (editing != null) R.string.booking_updated else R.string.booking_success
                        ),
                        Toast.LENGTH_LONG
                    ).show()
                    returnToBookingsList()
                } else {
                    showSummaryBookingError(response.errorBody()?.string())
                }
            } catch (e: Exception) {
                if (_binding == null) return@launch
                submitting = false
                binding.btnConfirmBooking.isEnabled = true
                showSummaryBookingError(e.message)
            }
        }
    }

    private fun monthLabel(): String {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
    }

    private fun formatLongDate(iso: String?): String {
        val instant = BookingRules.parseInstant(iso) ?: return "—"
        val zoned = instant.atZone(ZoneId.of("Asia/Colombo"))
        return zoned.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.getDefault()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
