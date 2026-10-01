package com.ead.solargrid.ui.operator

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ead.solargrid.R
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.databinding.ActivityGridOperatorHomeBinding
import com.ead.solargrid.databinding.ItemOperatorActionBinding
import com.ead.solargrid.databinding.ItemOperatorStatBinding
import com.ead.solargrid.ui.auth.LoginActivity
import com.ead.solargrid.ui.operator.reservations.OperatorReservationsActivity
import com.ead.solargrid.ui.operator.scan.ScanQrActivity
import com.ead.solargrid.ui.operator.stations.OperatorStationsActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Calendar

class GridOperatorHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGridOperatorHomeBinding
    private lateinit var viewModel: GridOperatorHomeViewModel
    private lateinit var sessionManager: SessionManager

    /** Completing, approving or rejecting changes the numbers, so refresh on the way back. */
    private val openAndRefresh = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (::viewModel.isInitialized) viewModel.refresh()
    }

    private val countFormat = NumberFormat.getIntegerInstance()
    private val kwFormat = DecimalFormat("#,##0.#")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)

        // Guard against a stale launch (e.g. from recents) after the session was cleared.
        if (sessionManager.fetchAuthToken().isNullOrEmpty()) {
            openLogin()
            return
        }

        binding = ActivityGridOperatorHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this)[GridOperatorHomeViewModel::class.java]

        setUpWindowInsets()
        setUpCards()
        setUpActions()

        binding.btnRefresh.setOnClickListener { viewModel.refresh() }
        binding.btnRetry.setOnClickListener { viewModel.refresh() }
        binding.btnLogout.setOnClickListener { confirmLogout() }
        binding.btnHeaderScan.setOnClickListener { openScanner() }

        viewModel.state.observe(this, ::render)

        // Keep "Updated x min ago" and the greeting current while the screen is visible.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    renderGreeting()
                    renderUpdatedLabel(viewModel.state.value ?: OperatorHomeState())
                    delay(DateUtils.MINUTE_IN_MILLIS)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) viewModel.refreshIfStale()
    }

    // ----- Setup -----

    /** Draw behind the system bars and pad the header / content so nothing sits under them. */
    private fun setUpWindowInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, binding.root).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val header = binding.header
        val content = binding.content
        val headerPadding = intArrayOf(header.paddingLeft, header.paddingTop, header.paddingRight)
        val contentPadding = intArrayOf(content.paddingLeft, content.paddingRight, content.paddingBottom)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            header.updatePadding(
                left = headerPadding[0] + bars.left,
                top = headerPadding[1] + bars.top,
                right = headerPadding[2] + bars.right
            )
            content.updatePadding(
                left = contentPadding[0] + bars.left,
                right = contentPadding[1] + bars.right,
                bottom = contentPadding[2] + bars.bottom
            )
            binding.progress.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin = bars.top }
            insets
        }
    }

    private fun setUpCards() {
        binding.cardPending.setUp(R.drawable.ic_op_schedule, R.string.operator_stat_pending, OperatorTone.AMBER, R.string.operator_stat_pending_hint) {
            openReservations(OperatorReservationsActivity.pendingIntent(this))
        }
        binding.cardUpcoming.setUp(R.drawable.ic_op_event_available, R.string.operator_stat_upcoming, OperatorTone.GREEN, R.string.operator_stat_upcoming_hint) {
            openReservations(OperatorReservationsActivity.allIntent(this, status = OperatorReservationStatus.APPROVED))
        }
        binding.cardToday.setUp(R.drawable.ic_op_today, R.string.operator_stat_today, OperatorTone.BLUE, R.string.operator_stat_today_hint) {
            openReservations(OperatorReservationsActivity.allIntent(this, dateUtc = todayUtc()))
        }
        binding.cardCompleted.setUp(R.drawable.ic_op_check_circle, R.string.operator_stat_completed, OperatorTone.ORANGE, R.string.operator_stat_completed_hint) {
            openReservations(
                OperatorReservationsActivity.allIntent(this, status = OperatorReservationStatus.COMPLETED, dateUtc = todayUtc())
            )
        }

        // Infrastructure cards are three across, so they use a smaller number.
        listOf(
            Triple(binding.cardStations, R.drawable.ic_op_station, R.string.operator_stat_stations),
            Triple(binding.cardOutput, R.drawable.ic_op_sun, R.string.operator_stat_output),
            Triple(binding.cardBattery, R.drawable.ic_op_battery, R.string.operator_stat_battery)
        ).forEach { (card, icon, label) ->
            card.setUp(icon, label, OperatorTone.NEUTRAL)
            card.tvValue.textSize = 20f
        }
        // Output and battery are station totals, so all three open the station list.
        listOf(binding.cardStations, binding.cardOutput, binding.cardBattery).forEach { card ->
            card.makeClickable { openStations() }
        }
    }

    private fun setUpActions() {
        binding.actionScan.setUp(R.drawable.ic_op_qr, R.string.operator_action_scan, OperatorTone.YELLOW) {
            openScanner()
        }
        binding.actionPending.setUp(R.drawable.ic_op_schedule, R.string.operator_action_pending, OperatorTone.AMBER) {
            openReservations(OperatorReservationsActivity.pendingIntent(this))
        }
        binding.actionReservations.setUp(R.drawable.ic_op_list, R.string.operator_action_reservations, OperatorTone.BLUE) {
            openReservations(OperatorReservationsActivity.allIntent(this))
        }
        binding.actionStations.setUp(R.drawable.ic_op_station, R.string.operator_action_stations, OperatorTone.NEUTRAL) {
            openStations()
        }
    }

    private fun ItemOperatorStatBinding.setUp(
        @DrawableRes icon: Int,
        @StringRes label: Int,
        tone: OperatorTone,
        @StringRes hint: Int? = null,
        onClick: (() -> Unit)? = null
    ) {
        OperatorScreen.applyTone(iconBadge, ivIcon, icon, tone)
        tvLabel.setText(label)
        if (hint != null) {
            tvHint.setText(hint)
            tvHint.isVisible = true
        }
        if (onClick != null) makeClickable(onClick)
    }

    private fun ItemOperatorStatBinding.makeClickable(onClick: () -> Unit) {
        root.isClickable = true
        root.isFocusable = true
        root.setOnClickListener { onClick() }
    }

    private fun ItemOperatorActionBinding.setUp(
        @DrawableRes icon: Int,
        @StringRes label: Int,
        tone: OperatorTone,
        onClick: () -> Unit
    ) {
        OperatorScreen.applyTone(iconBadge, ivIcon, icon, tone)
        tvLabel.setText(label)
        root.setOnClickListener { onClick() }
    }

    /** The UTC day the API's dateUtc filter uses, matching the "today" cards. */
    private fun todayUtc(): String = LocalDate.now(ZoneOffset.UTC).toString()

    // ----- Rendering -----

    private fun render(state: OperatorHomeState) {
        if (state.sessionExpired) {
            Toast.makeText(this, R.string.operator_session_expired, Toast.LENGTH_LONG).show()
            logout()
            return
        }

        if (state.isLoading) binding.progress.show() else binding.progress.hide()
        binding.btnRefresh.isEnabled = !state.isLoading

        binding.cardPending.showValue(state.summary?.pendingCount?.let(countFormat::format))
        binding.cardUpcoming.showValue(state.summary?.approvedFutureCount?.let(countFormat::format))
        binding.cardToday.showValue(state.reservationsToday?.let(countFormat::format))
        binding.cardCompleted.showValue(state.completedToday?.let(countFormat::format))

        val infra = state.infrastructure
        binding.cardStations.showValue(infra?.stations?.let { countFormat.format(it.toLong()) })
        binding.cardOutput.showValue(infra?.capacityKw?.let { getString(R.string.operator_value_kw, kwFormat.format(it)) })
        binding.cardBattery.showValue(infra?.batterySlots?.let { countFormat.format(it.toLong()) })

        binding.errorCard.isVisible = state.errorMessage != null && !state.isLoading
        binding.tvError.text = state.errorMessage

        renderUpdatedLabel(state)
    }

    private fun ItemOperatorStatBinding.showValue(value: String?) {
        tvValue.text = value ?: getString(R.string.operator_value_placeholder)
        root.contentDescription = getString(
            R.string.operator_card_description,
            tvLabel.text,
            value ?: getString(R.string.operator_updated_never)
        )
    }

    private fun renderGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = getString(
            when {
                hour < 12 -> R.string.operator_greeting_morning
                hour < 17 -> R.string.operator_greeting_afternoon
                else -> R.string.operator_greeting_evening
            }
        )
        val firstName = sessionManager.getUserSession()?.name?.trim()?.split(Regex("\\s+"))?.firstOrNull()
        binding.tvGreeting.text = if (firstName.isNullOrEmpty()) {
            greeting
        } else {
            getString(R.string.operator_greeting_with_name, greeting, firstName)
        }
    }

    private fun renderUpdatedLabel(state: OperatorHomeState) {
        val updatedAt = state.updatedAtMillis
        val now = System.currentTimeMillis()
        binding.tvUpdated.text = when {
            state.isLoading && updatedAt != null -> getString(R.string.operator_refreshing)
            updatedAt == null -> getString(R.string.operator_updated_never)
            now - updatedAt < DateUtils.MINUTE_IN_MILLIS -> getString(R.string.operator_updated_just_now)
            else -> getString(
                R.string.operator_updated_ago,
                DateUtils.getRelativeTimeSpanString(updatedAt, now, DateUtils.MINUTE_IN_MILLIS)
            )
        }
    }

    // ----- Actions -----

    private fun openScanner() = openAndRefresh.launch(Intent(this, ScanQrActivity::class.java))

    private fun openReservations(intent: Intent) = openAndRefresh.launch(intent)

    private fun openStations() = startActivity(OperatorStationsActivity.newIntent(this))

    private fun confirmLogout() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.operator_logout_title)
            .setMessage(R.string.operator_logout_message)
            .setNegativeButton(R.string.operator_cancel, null)
            .setPositiveButton(R.string.operator_logout) { _, _ -> logout() }
            .show()
    }

    private fun logout() {
        sessionManager.logout()
        openLogin()
    }

    private fun openLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
