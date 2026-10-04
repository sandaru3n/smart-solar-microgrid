package com.ead.solargrid.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.ead.solargrid.R
import com.ead.solargrid.databinding.ActivityProsumerHomeBinding
import com.ead.solargrid.ui.SystemBarUtils

import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.ui.auth.LoginActivity
import kotlinx.coroutines.launch
import android.content.Intent

class ProsumerHomeActivity : AppCompatActivity(), ProsumerNavigator {

    companion object {
        private const val KEY_SELECTED_TAB = "selected_tab"
    }

    private lateinit var binding: ActivityProsumerHomeBinding
    private var ignoreBottomNavSelection = false

    private val dashboardFragment = ProsumerDashboardFragment()
    private val mapFragment = StationsMapFragment()
    private val bookingsFragment = MyReservationsFragment()
    private val historyFragment = BookingHistoryFragment()
    private val profileFragment = ProfileFragment()
    private var pendingNewBooking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProsumerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SystemBarUtils.enableEdgeToEdge(this, binding.root)
        SystemBarUtils.applyYellowStatusBarShell(
            root = binding.root,
            statusBarScrim = binding.statusBarScrim,
            bottomTarget = binding.bottomNav,
            horizontalTarget = binding.root
        )

        binding.brandHeader.btnBrandProfile.setOnClickListener { openProfileTab() }

        val tabId = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: R.id.nav_home

        ignoreBottomNavSelection = true
        binding.bottomNav.setOnItemSelectedListener { item ->
            if (ignoreBottomNavSelection) {
                return@setOnItemSelectedListener true
            }
            showFragmentForTab(item.itemId)
            true
        }

        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            showFragmentForTab(tabId)
        }
        binding.bottomNav.selectedItemId = tabId
        ignoreBottomNavSelection = false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::binding.isInitialized) {
            outState.putInt(KEY_SELECTED_TAB, binding.bottomNav.selectedItemId)
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            try {
                val session = SessionManager(this@ProsumerHomeActivity)
                val user = session.getUserSession()
                if (user != null) {
                    val api = ApiClient.getApiService(this@ProsumerHomeActivity)
                    val response = api.getUser(user.nic)
                    if (response.isSuccessful) {
                        val body = response.body()
                        if (body?.accountStatus == "DEACTIVATED") {
                            session.logout()
                            startActivity(Intent(this@ProsumerHomeActivity, LoginActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            })
                            finish()
                        } else {
                            session.saveUserSession(
                                nic = body!!.nic,
                                name = body.name,
                                email = body.email,
                                phone = body.phone.orEmpty(),
                                address = body.address.orEmpty(),
                                role = body.role,
                                accountStatus = body.accountStatus,
                                profilePicUrl = body.profilePicUrl
                            )
                            loadProfilePic(body.profilePicUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore network errors here
            }
        }
    }

    private fun loadProfilePic(url: String?) {
        if (url.isNullOrEmpty()) return
        try {
            com.bumptech.glide.Glide.with(this)
                .load(url)
                .circleCrop()
                .into(binding.brandHeader.btnBrandProfile)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showFragmentForTab(tabId: Int) {
        val fragment = when (tabId) {
            R.id.nav_map -> mapFragment
            R.id.nav_bookings -> bookingsFragment
            R.id.nav_history -> historyFragment
            R.id.nav_profile -> profileFragment
            else -> dashboardFragment
        }
        showFragment(fragment)
    }

    override fun openBookingsTab() {
        binding.bottomNav.selectedItemId = R.id.nav_bookings
    }

    override fun openNewBookingFlow() {
        pendingNewBooking = true
        openBookingsTab()
    }

    override fun openProfileTab() {
        binding.bottomNav.selectedItemId = R.id.nav_profile
    }

    fun consumePendingNewBooking(): Boolean {
        if (!pendingNewBooking) return false
        pendingNewBooking = false
        return true
    }

    override fun showNearbyStationsMessage() {
        binding.bottomNav.selectedItemId = R.id.nav_map
    }

    private fun showFragment(fragment: Fragment) {
        val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        if (current === fragment) {
            return
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
