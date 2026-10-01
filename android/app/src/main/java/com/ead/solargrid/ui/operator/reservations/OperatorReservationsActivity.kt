package com.ead.solargrid.ui.operator.reservations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.ead.solargrid.R
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.databinding.ActivityOperatorReservationsBinding
import com.ead.solargrid.models.ReservationItem
import com.ead.solargrid.ui.home.ReservationUi
import com.ead.solargrid.ui.operator.OperatorReservationStatus
import com.ead.solargrid.ui.operator.OperatorScreen
import com.ead.solargrid.ui.operator.OperatorTone
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Grid Operator reservation lists, in two modes:
 *  - Pending approvals: Pending only, newest booking first, approve / reject inline.
 *  - All reservations: status chips, slot date, station and text search, paged.
 * Result is [RESULT_OK] when any reservation changed, so the dashboard refreshes its numbers.
 */
class OperatorReservationsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_PENDING_ONLY = "pending_only"
        private const val EXTRA_STATUS = "status"
        private const val EXTRA_DATE_UTC = "date_utc"
        private const val EXTRA_STATION_ID = "station_id"
        private const val EXTRA_STATION_NAME = "station_name"

        /** Rows left below the last visible one before the next page is requested. */
        private const val LOAD_MORE_THRESHOLD = 4

        fun pendingIntent(context: Context): Intent =
            Intent(context, OperatorReservationsActivity::class.java)
                .putExtra(EXTRA_PENDING_ONLY, true)

        fun allIntent(
            context: Context,
            status: OperatorReservationStatus? = null,
            dateUtc: String? = null,
            stationId: String? = null,
            stationName: String? = null
        ): Intent = Intent(context, OperatorReservationsActivity::class.java)
            .putExtra(EXTRA_STATUS, status?.name)
            .putExtra(EXTRA_DATE_UTC, dateUtc)
            .putExtra(EXTRA_STATION_ID, stationId)
            .putExtra(EXTRA_STATION_NAME, stationName)
    }

    private lateinit var binding: ActivityOperatorReservationsBinding
    private lateinit var viewModel: OperatorReservationsViewModel
    private lateinit var adapter: OperatorReservationAdapter

    private val pendingOnly by lazy { intent.getBooleanExtra(EXTRA_PENDING_ONLY, false) }

    /** Chip view id -> status filter (null = All). */
    private val statusChipIds = mutableMapOf<Int, OperatorReservationStatus?>()

    /** So a failed refresh with rows on screen is announced once, not on every render. */
    private var shownError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SessionManager(this).fetchAuthToken().isNullOrEmpty()) {
            OperatorScreen.sessionExpired(this)
            return
        }

        binding = ActivityOperatorReservationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[OperatorReservationsViewModel::class.java]

        OperatorScreen.applyInsets(this, binding.root, binding.topBar, binding.list)
        binding.tvTitle.setText(if (pendingOnly) R.string.operator_action_pending else R.string.operator_action_reservations)
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnRefresh.setOnClickListener { viewModel.refresh() }

        setUpList()
        setUpFilters()

        viewModel.init(initialFilter())
        viewModel.state.observe(this, ::render)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect(::onEvent)
            }
        }
    }

    private fun initialFilter(): ReservationFilter {
        if (pendingOnly) return ReservationFilter(status = OperatorReservationStatus.PENDING)
        return ReservationFilter(
            status = intent.getStringExtra(EXTRA_STATUS)?.let { name ->
                OperatorReservationStatus.entries.firstOrNull { it.name == name }
            },
            dateUtc = intent.getStringExtra(EXTRA_DATE_UTC),
            stationId = intent.getStringExtra(EXTRA_STATION_ID),
            stationName = intent.getStringExtra(EXTRA_STATION_NAME)
        )
    }

    // ----- Setup -----

    private fun setUpList() {
        adapter = OperatorReservationAdapter(
            onApprove = viewModel::approve,
            onReject = ::confirmReject,
            onRetryMore = viewModel::loadMore
        )
        val layoutManager = LinearLayoutManager(this)
        binding.list.layoutManager = layoutManager
        binding.list.adapter = adapter
        binding.list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val state = viewModel.state.value ?: return
                if (state.loadMoreFailed) return // wait for the footer's Retry
                if (layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - LOAD_MORE_THRESHOLD) {
                    viewModel.loadMore()
                }
            }
        })

        binding.swipeRefresh.setColorSchemeResources(R.color.op_primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.op_surface)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        // The list sits in a FrameLayout with the empty state, so tell the layout what can scroll.
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ -> binding.list.canScrollVertically(-1) }
    }

    private fun setUpFilters() {
        binding.filters.isVisible = !pendingOnly
        if (pendingOnly) return

        val statuses = listOf<OperatorReservationStatus?>(null) + OperatorReservationStatus.entries
        statuses.forEach { status ->
            val chip = layoutInflater.inflate(R.layout.item_operator_filter_chip, binding.statusChips, false) as Chip
            chip.id = View.generateViewId()
            chip.text = status?.let { getString(it.label) } ?: getString(R.string.op_res_filter_all)
            statusChipIds[chip.id] = status
            binding.statusChips.addView(chip)
        }
        binding.statusChips.setOnCheckedStateChangeListener { _, checkedIds ->
            val id = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            viewModel.setStatus(statusChipIds[id])
        }

        binding.etSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.etSearch.setOnEditorActionListener { view, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
                view.clearFocus()
                true
            } else {
                false
            }
        }

        binding.chipDate.setOnClickListener { pickDate() }
        binding.chipDate.setOnCloseIconClickListener { viewModel.setDate(null) }
        binding.chipStation.setOnCloseIconClickListener { viewModel.clearStation() }
    }

    private fun pickDate() {
        val selected = viewModel.state.value?.filter?.dateUtc
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now(ZoneOffset.UTC)
        // MaterialDatePicker works in UTC midnight millis, which is exactly the API's dateUtc day.
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.op_res_pick_date)
            .setSelection(selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            viewModel.setDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
        }
        picker.show(supportFragmentManager, "slot_date")
    }

    // ----- Rendering -----

    private fun render(state: ReservationListState) {
        if (state.sessionExpired) {
            OperatorScreen.sessionExpired(this)
            return
        }

        renderSubtitle(state)
        renderFilters(state.filter)

        val rows = state.items.map { ReservationRow.Reservation(it, it.id in state.busyIds) }
        val footer = if (state.isLoadingMore || state.loadMoreFailed) {
            listOf(ReservationRow.Footer(failed = state.loadMoreFailed))
        } else {
            emptyList()
        }
        adapter.submitList(rows + footer)

        // Rows on screen: the pull-to-refresh spinner. Nothing yet: the centred spinner.
        val hasRows = state.items.isNotEmpty()
        binding.swipeRefresh.isRefreshing = state.isRefreshing && hasRows
        binding.btnRefresh.isEnabled = !state.isRefreshing
        renderEmptyState(state, hasRows)

        val error = state.errorMessage
        if (error != null && hasRows && error != shownError) {
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                .setAction(R.string.operator_retry) { viewModel.refresh() }
                .show()
        }
        shownError = error
    }

    private fun renderSubtitle(state: ReservationListState) {
        val total = state.totalCount
        binding.tvSubtitle.text = when {
            total == null -> getString(R.string.operator_updated_never)
            pendingOnly -> resources.getQuantityString(R.plurals.op_res_pending_count, total.toInt(), total.toInt())
            else -> resources.getQuantityString(R.plurals.op_res_count, total.toInt(), total.toInt())
        }
    }

    private fun renderFilters(filter: ReservationFilter) {
        if (pendingOnly) return

        val chipId = statusChipIds.entries.firstOrNull { it.value == filter.status }?.key
        if (chipId != null && binding.statusChips.checkedChipId != chipId) binding.statusChips.check(chipId)

        val date = filter.dateUtc?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        binding.chipDate.text = if (date != null) {
            getString(R.string.op_res_date_value, date.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())))
        } else {
            getString(R.string.op_res_date_any)
        }
        binding.chipDate.isCloseIconVisible = date != null

        binding.chipStation.isVisible = filter.stationId != null
        binding.chipStation.text = filter.stationName ?: getString(R.string.op_res_unknown_station)
    }

    private fun renderEmptyState(state: ReservationListState, hasRows: Boolean) {
        val empty = binding.emptyState
        if (hasRows) {
            empty.root.isVisible = false
            return
        }
        empty.root.isVisible = true
        val loading = state.isRefreshing || !state.hasLoaded && state.errorMessage == null
        empty.progress.isVisible = loading
        empty.messageGroup.isVisible = !loading
        if (loading) return

        val error = state.errorMessage
        when {
            error != null -> showMessage(
                R.drawable.ic_op_error, OperatorTone.RED,
                getString(R.string.op_res_error_title), error,
                getString(R.string.operator_retry)
            ) { viewModel.refresh() }

            pendingOnly -> showMessage(
                R.drawable.ic_op_check_circle, OperatorTone.GREEN,
                getString(R.string.op_res_pending_empty_title), getString(R.string.op_res_pending_empty_message),
                null, null
            )

            state.filter.isFiltered -> showMessage(
                R.drawable.ic_op_search, OperatorTone.NEUTRAL,
                getString(R.string.op_res_filtered_empty_title), getString(R.string.op_res_filtered_empty_message),
                getString(R.string.op_res_clear_filters)
            ) {
                binding.etSearch.setText("")
                viewModel.clearFilters(keepStatus = false)
            }

            else -> showMessage(
                R.drawable.ic_op_inbox, OperatorTone.NEUTRAL,
                getString(R.string.op_res_empty_title), getString(R.string.op_res_empty_message),
                null, null
            )
        }
    }

    private fun showMessage(
        @DrawableRes icon: Int,
        tone: OperatorTone,
        title: String,
        message: String,
        action: String?,
        onAction: (() -> Unit)?
    ) {
        val empty = binding.emptyState
        OperatorScreen.applyTone(empty.iconBadge, empty.ivIcon, icon, tone)
        empty.tvTitle.text = title
        empty.tvMessage.text = message
        empty.btnAction.isVisible = action != null
        empty.btnAction.text = action
        empty.btnAction.setOnClickListener { onAction?.invoke() }
    }

    // ----- Actions -----

    private fun confirmReject(item: ReservationItem) {
        val station = item.stationName?.takeIf { it.isNotBlank() } ?: getString(R.string.op_res_unknown_station)
        val slot = ReservationUi.formatSlotRange(item.slotStartTimeUtc, item.slotEndTimeUtc)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.op_res_reject_title)
            .setMessage(getString(R.string.op_res_reject_message, station, slot, item.prosumerId))
            .setNegativeButton(R.string.op_res_keep_pending, null)
            .setPositiveButton(R.string.op_res_reject) { _, _ -> viewModel.reject(item) }
            .show()
    }

    private fun onEvent(event: ReservationEvent) {
        when (event) {
            ReservationEvent.Changed -> setResult(RESULT_OK)
            is ReservationEvent.Message -> Snackbar.make(binding.root, event.text, Snackbar.LENGTH_LONG).show()
        }
    }
}
