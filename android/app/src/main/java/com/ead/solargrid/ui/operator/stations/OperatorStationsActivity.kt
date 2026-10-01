package com.ead.solargrid.ui.operator.stations

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.ead.solargrid.R
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.databinding.ActivityOperatorStationsBinding
import com.ead.solargrid.ui.operator.OperatorScreen
import com.ead.solargrid.ui.operator.OperatorTone
import com.google.android.material.snackbar.Snackbar

/** Grid Operator: every active station, searchable on device. Each row opens the station details. */
class OperatorStationsActivity : AppCompatActivity() {

    companion object {
        fun newIntent(context: Context) = Intent(context, OperatorStationsActivity::class.java)
    }

    private lateinit var binding: ActivityOperatorStationsBinding
    private lateinit var viewModel: OperatorStationsViewModel
    private lateinit var adapter: OperatorStationAdapter

    private var shownError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (SessionManager(this).fetchAuthToken().isNullOrEmpty()) {
            OperatorScreen.sessionExpired(this)
            return
        }

        binding = ActivityOperatorStationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[OperatorStationsViewModel::class.java]

        OperatorScreen.applyInsets(this, binding.root, binding.topBar, binding.list)
        binding.btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.btnRefresh.setOnClickListener { viewModel.refresh() }

        adapter = OperatorStationAdapter { station ->
            startActivity(OperatorStationDetailActivity.newIntent(this, station.id, station.name))
        }
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.swipeRefresh.setColorSchemeResources(R.color.op_primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.op_surface)
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ -> binding.list.canScrollVertically(-1) }

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

        viewModel.state.observe(this, ::render)
        viewModel.loadIfNeeded()
    }

    private fun render(state: StationListState) {
        if (state.sessionExpired) {
            OperatorScreen.sessionExpired(this)
            return
        }

        val stations = state.stations
        binding.tvSubtitle.text = if (stations == null) {
            getString(R.string.operator_updated_never)
        } else {
            getString(
                R.string.op_station_subtitle,
                resources.getQuantityString(R.plurals.op_station_count, stations.size, stations.size),
                StationFormat.kw(this, stations.sumOf { it.capacityKw })
            )
        }

        val visible = state.visible
        adapter.submitList(visible)

        val hasRows = visible.isNotEmpty()
        binding.swipeRefresh.isRefreshing = state.isLoading && hasRows
        binding.btnRefresh.isEnabled = !state.isLoading
        renderEmptyState(state, hasRows)

        val error = state.errorMessage
        if (error != null && hasRows && error != shownError) {
            Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG)
                .setAction(R.string.operator_retry) { viewModel.refresh() }
                .show()
        }
        shownError = error
    }

    private fun renderEmptyState(state: StationListState, hasRows: Boolean) {
        val empty = binding.emptyState
        empty.root.isVisible = !hasRows
        if (hasRows) return

        val loading = state.isLoading || state.stations == null && state.errorMessage == null
        empty.progress.isVisible = loading
        empty.messageGroup.isVisible = !loading
        if (loading) return

        val error = state.errorMessage
        val searching = state.query.isNotBlank() && !state.stations.isNullOrEmpty()
        val (icon, tone) = when {
            error != null && state.stations == null -> R.drawable.ic_op_error to OperatorTone.RED
            searching -> R.drawable.ic_op_search to OperatorTone.NEUTRAL
            else -> R.drawable.ic_op_station to OperatorTone.NEUTRAL
        }
        OperatorScreen.applyTone(empty.iconBadge, empty.ivIcon, icon, tone)
        when {
            error != null && state.stations == null -> {
                empty.tvTitle.setText(R.string.op_station_error_title)
                empty.tvMessage.text = error
                empty.btnAction.setText(R.string.operator_retry)
                empty.btnAction.setOnClickListener { viewModel.refresh() }
            }
            searching -> {
                empty.tvTitle.setText(R.string.op_station_search_empty_title)
                empty.tvMessage.text = getString(R.string.op_station_search_empty_message, state.query.trim())
                empty.btnAction.setText(R.string.op_station_clear_search)
                empty.btnAction.setOnClickListener { binding.etSearch.setText("") }
            }
            else -> {
                empty.tvTitle.setText(R.string.op_station_empty_title)
                empty.tvMessage.setText(R.string.op_station_empty_message)
                empty.btnAction.setText(R.string.operator_refresh)
                empty.btnAction.setOnClickListener { viewModel.refresh() }
            }
        }
        empty.btnAction.isVisible = true
    }
}
