package com.ead.solargrid.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.R
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.databinding.FragmentProsumerMapBinding
import com.ead.solargrid.models.SolarStation
import com.ead.solargrid.models.StationSchedule
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.launch
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Nearby Stations map screen.
 *
 * Uses the official recommended pattern from the Maps SDK for Android docs:
 * a [SupportMapFragment] hosted in the child fragment manager, with the map
 * delivered through [OnMapReadyCallback]. Lifecycle is handled automatically
 * by the fragment (no manual MapView lifecycle forwarding needed).
 *
 * Author: M.S.N. Peiris it23201132
 * Date: 2026
 */
class StationsMapFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentProsumerMapBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var userLocation: LatLng? = null
    private var selected: SolarStation? = null
    // Prevents suggestion dropdown from reopening while we fill the search box.
    private var applyingSuggestion = false
    private var sheetShown = false
    // Stations sorted nearest-first for swipe left/right on the bottom sheet.
    private var nearbyOrder: List<SolarStation> = emptyList()
    private var scheduleExpanded = false
    // TranslationY that keeps the schedule peek hidden while showing station details.
    private var collapsedOffset = 0
    private var scheduleLoadedFor: String? = null
    private var entranceRunning = false
    private var sheetSettling = false
    private var verticalDrag = false
    private var dragAnchorY = 0f
    private var stationIcon: BitmapDescriptor? = null
    private val markers = mutableListOf<Pair<Marker, SolarStation>>()

    // Runtime location permission callback — enables my-location when the user grants access.
    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            enableMyLocation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProsumerMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Warn if the Maps API key is missing from the app manifest.
        if (manifestMapsKey().isBlank()) {
            binding.tvMapKeyMissing.visibility = View.VISIBLE
        }
        binding.btnCloseLocation.setOnClickListener {
            binding.locationBanner.visibility = View.GONE
        }
        binding.btnUseLocation.setOnClickListener { requestLocation() }
        binding.btnMyLocation.setOnClickListener { requestLocation() }
        enableSheetGestures()
        // Live search filters markers and shows name/address suggestions.
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                filterMarkers(query)
                if (!applyingSuggestion) showSuggestions(query)
            }
        })

        // Official pattern: host SupportMapFragment in the child fragment manager.
        val mapFragment = childFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it)
                    .commitNow()
            }
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        // View may already be destroyed if the callback arrives late.
        if (_binding == null) return
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_NORMAL
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isMapToolbarEnabled = false
        // Marker tap opens the station bottom sheet; map tap clears search suggestions.
        map.setOnMarkerClickListener { marker ->
            (marker.tag as? SolarStation)?.let { showStation(it) }
            hideSuggestions()
            false
        }
        map.setOnMapClickListener { hideSuggestions() }
        // Default camera centres on Colombo until GPS is available.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(COLOMBO, 12f))
        if (hasLocationPermission()) {
            enableMyLocation()
        }
        loadStations()
    }

    override fun onDestroyView() {
        // Clear map references so nothing holds the destroyed view.
        markers.clear()
        googleMap = null
        stationIcon = null
        sheetShown = false
        scheduleExpanded = false
        collapsedOffset = 0
        scheduleLoadedFor = null
        entranceRunning = false
        sheetSettling = false
        verticalDrag = false
        _binding = null
        super.onDestroyView()
    }

    /** Reads the Google Maps API key from the application meta-data. */
    private fun manifestMapsKey(): String {
        val context = context ?: return ""
        return try {
            val info = context.packageManager.getApplicationInfo(
                context.packageName,
                PackageManager.GET_META_DATA
            )
            info.metaData?.getString("com.google.android.geo.API_KEY").orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /** Requests location permission if needed, otherwise turns on my-location. */
    private fun requestLocation() {
        if (hasLocationPermission()) {
            enableMyLocation()
            return
        }
        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun hasLocationPermission(): Boolean {
        val context = context ?: return false
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Enables the blue my-location dot and recentres the camera on the last known GPS fix. */
    private fun enableMyLocation() {
        val map = googleMap ?: return
        if (!hasLocationPermission()) return
        try {
            map.isMyLocationEnabled = true
        } catch (_: SecurityException) {
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(requireActivity())
        client.lastLocation.addOnSuccessListener { location ->
            if (location == null || _binding == null) return@addOnSuccessListener
            userLocation = LatLng(location.latitude, location.longitude)
            binding.locationBanner.visibility = View.GONE
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation!!, 13f))
            // Refresh distance text for the currently selected station.
            selected?.let { showStation(it) }
            // Reload so nearby stations are ordered from the user's position.
            loadStations()
        }
    }

    /**
     * Loads stations from the API.
     * Prefers /stations/nearby when GPS is known; falls back to the full active list.
     */
    private fun loadStations() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val here = userLocation
                val stations = if (here != null) {
                    // 50 km nearby search; if empty, show all active stations instead.
                    val nearby = api.getNearbyStations(here.latitude, here.longitude, 50.0).body().orEmpty()
                    if (nearby.isNotEmpty()) {
                        nearby.map { it.station }
                    } else {
                        api.getStations().body().orEmpty()
                    }
                } else {
                    api.getStations().body().orEmpty()
                }.filter { it.isActive && it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }

                if (_binding == null) return@launch
                drawMarkers(stations)
            } catch (_: Exception) {
                if (_binding == null) return@launch
                binding.tvStationName.setText(R.string.dashboard_load_error)
                pullUpSheet()
            }
        }
    }

    /** Places custom markers for each station and focuses the nearest one. */
    private fun drawMarkers(stations: List<SolarStation>) {
        val map = googleMap ?: return
        markers.forEach { it.first.remove() }
        markers.clear()
        stations.forEach { station ->
            val marker = map.addMarker(
                MarkerOptions()
                    .position(LatLng(station.latitude, station.longitude))
                    .title(station.name)
                    .anchor(0.5f, 0.5f)
                    .icon(stationMarkerIcon())
            ) ?: return@forEach
            // Tag keeps the SolarStation model attached to the marker click.
            marker.tag = station
            markers += marker to station
        }
        filterMarkers(binding.etSearch.text?.toString().orEmpty())
        val origin = userLocation ?: COLOMBO
        nearbyOrder = stations.sortedBy { distanceKm(origin, LatLng(it.latitude, it.longitude)) }
        val focus = nearbyOrder.firstOrNull()
        if (focus != null) {
            showStation(focus)
        }
        moveCamera(stations)
    }

    /** Builds (and caches) the yellow thunderbolt marker icon. */
    private fun stationMarkerIcon(): BitmapDescriptor {
        stationIcon?.let { return it }
        val size = (44 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFACC15.toInt() }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, paint)
        val thunder = BitmapFactory.decodeResource(resources, R.drawable.thunder)
        if (thunder != null) {
            val inset = (size * 0.22f).toInt()
            canvas.drawBitmap(thunder, null, Rect(inset, inset, size - inset, size - inset), null)
            thunder.recycle()
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap).also { stationIcon = it }
    }

    /**
     * Positions the camera:
     * - on the user when GPS is available
     * - otherwise on stations near Colombo, or all stations if none are nearby
     */
    private fun moveCamera(stations: List<SolarStation>) {
        val map = googleMap ?: return
        val here = userLocation
        if (here != null) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(here, 13f))
            return
        }
        // Prefer stations within ~80 km of Colombo for a sensible default view.
        val nearbyColombo = stations.filter { distanceKm(COLOMBO, LatLng(it.latitude, it.longitude)) <= 80 }
        val focus = nearbyColombo.ifEmpty { stations }
        if (focus.isEmpty()) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(COLOMBO, 12f))
            return
        }
        if (focus.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus[0].latitude, focus[0].longitude), 13f))
            return
        }
        // Fit multiple markers into one camera bounds.
        val bounds = LatLngBounds.builder()
        focus.forEach { bounds.include(LatLng(it.latitude, it.longitude)) }
        binding.mapContainer.post {
            try {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 120))
            } catch (_: Exception) {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(COLOMBO, 12f))
            }
        }
    }

    /** Fills the bottom sheet with station details, distance and weekly schedule. */
    private fun showStation(station: SolarStation) {
        val selectedChanged = selected?.id != station.id
        selected = station
        binding.tvStationName.text = station.name
        binding.tvArea.text = station.address?.takeIf { it.isNotBlank() } ?: getString(R.string.map_station_area)
        binding.tvOpen.setText(if (station.isActive) R.string.map_open_now else R.string.map_closed)
        binding.tvPower.text = getString(R.string.map_power_kw, station.capacityKw.toInt())
        binding.tvSlots.text = getString(R.string.map_slots_count, station.batteryStorageSlots)
        val here = userLocation
        binding.tvDistance.text = if (here == null) {
            getString(R.string.map_distance_unknown)
        } else {
            getString(R.string.map_distance_km, distanceKm(here, LatLng(station.latitude, station.longitude)))
        }
        // Only reset schedule UI when switching to a different station.
        if (selectedChanged) resetSchedule()
        loadSchedule(station)
        if (sheetShown) syncPeek() else pullUpSheet()
    }

    /** Animates the bottom sheet up from below the screen into the collapsed (peek) position. */
    private fun pullUpSheet() {
        if (sheetShown || _binding == null) return
        sheetShown = true
        val sheet = binding.stationSheet
        if (binding.scheduleRows.childCount == 0) {
            binding.scheduleRows.addView(scheduleLine(getString(R.string.map_schedule_empty), bold = false))
        }
        binding.schedulePanel.visibility = View.VISIBLE
        binding.tvSlideHours.alpha = 1f
        sheet.post {
            if (_binding == null) return@post
            // Peek height equals the schedule panel so hours stay just off-screen.
            val peek = (binding.schedulePanel.height + topMargin(binding.schedulePanel)).coerceAtLeast(0)
            collapsedOffset = peek
            sheet.translationX = 0f
            sheet.translationY = sheet.height.toFloat()
            sheet.visibility = View.VISIBLE
            entranceRunning = true
            sheet.animate()
                .translationY(peek.toFloat())
                .setDuration(420)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    entranceRunning = false
                    if (_binding == null || verticalDrag || scheduleExpanded) return@withEndAction
                    // Re-measure after layout settles — panel height can change once content is drawn.
                    val extra = binding.schedulePanel.height + topMargin(binding.schedulePanel)
                    if (extra > 0) {
                        collapsedOffset = extra
                        sheet.translationY = extra.toFloat()
                    }
                    updateHoursHint()
                }
                .start()
        }
    }

    /** Clears schedule rows when the selected station changes. */
    private fun resetSchedule() {
        scheduleExpanded = false
        scheduleLoadedFor = null
        collapsedOffset = 0
        if (_binding == null) return
        binding.tvSlideHours.alpha = 1f
        binding.scheduleRows.removeAllViews()
        binding.schedulePanel.visibility = View.GONE
        binding.stationSheet.translationY = 0f
    }

    /** Loads the weekly operating schedule for the selected station from the API. */
    private fun loadSchedule(station: SolarStation) {
        // Skip if this station's schedule is already loaded.
        if (scheduleLoadedFor == station.id) return
        val stationId = station.id
        binding.scheduleRows.removeAllViews()
        binding.scheduleRows.addView(scheduleLine(getString(R.string.map_schedule_empty), bold = false))
        viewLifecycleOwner.lifecycleScope.launch {
            val rows = try {
                ApiClient.getApiService(requireContext()).getStationSchedules(stationId).body().orEmpty()
            } catch (_: Exception) {
                emptyList()
            }
            // Ignore stale responses if the user already switched stations.
            if (_binding == null || selected?.id != stationId) return@launch
            renderSchedule(rows)
            scheduleLoadedFor = stationId
            syncPeek()
        }
    }

    /** Re-parks the sheet at the collapsed peek after schedule content changes height. */
    private fun syncPeek() {
        val panel = binding.schedulePanel
        val sheet = binding.stationSheet
        if (_binding == null || entranceRunning || sheetSettling || verticalDrag || scheduleExpanded || !sheetShown) return
        panel.visibility = View.VISIBLE
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                v.removeOnLayoutChangeListener(this)
                parkSchedule()
            }
        }
        sheet.addOnLayoutChangeListener(listener)
        sheet.requestLayout()
        sheet.post { sheet.removeOnLayoutChangeListener(listener) }
        if (panel.height > 0) parkSchedule()
    }

    /** Sets translationY so only station details show and the schedule stays peeked off-screen. */
    private fun parkSchedule() {
        if (_binding == null || entranceRunning || sheetSettling || verticalDrag || scheduleExpanded || !sheetShown) return
        val panel = binding.schedulePanel
        val extra = panel.height + topMargin(panel)
        if (extra <= 0) return
        collapsedOffset = extra
        binding.stationSheet.translationY = extra.toFloat()
        updateHoursHint()
    }

    /** Renders Monday–Sunday schedule rows into the sheet. */
    private fun renderSchedule(schedules: List<StationSchedule>) {
        val rows = binding.scheduleRows
        rows.removeAllViews()
        if (schedules.isEmpty()) {
            rows.addView(scheduleLine(getString(R.string.map_schedule_empty), bold = false))
            return
        }
        val order = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        val sorted = schedules.sortedBy { schedule ->
            val index = order.indexOfFirst { it.equals(schedule.day, ignoreCase = true) }
            if (index < 0) order.size else index
        }
        sorted.forEach { schedule ->
            val hours = if (schedule.isAvailable) {
                getString(R.string.map_schedule_hours, clock(schedule.openingTime), clock(schedule.closingTime))
            } else {
                getString(R.string.map_closed)
            }
            val line = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (6 * resources.displayMetrics.density).toInt(), 0, (6 * resources.displayMetrics.density).toInt())
            }
            line.addView(TextView(requireContext()).apply {
                text = schedule.day
                setTextColor(0xFF0B1C30.toInt())
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            line.addView(TextView(requireContext()).apply {
                text = hours
                setTextColor(if (schedule.isAvailable) 0xFF0B1C30.toInt() else 0xFF555E74.toInt())
                textSize = 13f
            })
            rows.addView(line)
        }
    }

    private fun scheduleLine(text: String, bold: Boolean): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setTextColor(0xFF555E74.toInt())
            textSize = 13f
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
    }

    /** Formats "HH:mm:ss" (or similar) down to a short "HH:mm" clock string. */
    private fun clock(value: String): String {
        return if (value.length >= 5) value.take(5) else value
    }

    /**
     * Sheet gestures:
     * - vertical drag expands / collapses / hides the sheet
     * - horizontal drag swipes between nearby stations
     */
    private fun enableSheetGestures() {
        val sheet = binding.stationSheet
        val slop = android.view.ViewConfiguration.get(sheet.context).scaledTouchSlop
        val velocity = android.view.VelocityTracker.obtain()
        var downX = 0f
        var downY = 0f
        // 0 = undecided, 1 = vertical, 2 = horizontal
        var mode = 0
        sheet.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    velocity.clear()
                    velocity.addMovement(event)
                    downX = event.rawX
                    downY = event.rawY
                    mode = 0
                    verticalDrag = false
                    sheet.animate().cancel()
                    entranceRunning = false
                    sheetSettling = false
                    dragAnchorY = sheet.translationY
                    sheet.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    velocity.addMovement(event)
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    // Decide gesture axis once movement exceeds the touch slop.
                    if (mode == 0) {
                        if (kotlin.math.abs(dx) < slop && kotlin.math.abs(dy) < slop) {
                            return@setOnTouchListener false
                        }
                        mode = if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) 1 else 2
                        if (mode == 1) verticalDrag = true
                        sheet.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (mode == 1) {
                        val maxY = sheet.height.toFloat().coerceAtLeast(0f)
                        sheet.translationY = (dragAnchorY + dy).coerceIn(0f, maxY)
                        updateHoursHint()
                        true
                    } else {
                        sheet.translationX = dx
                        true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    velocity.addMovement(event)
                    val was = mode
                    mode = 0
                    verticalDrag = false
                    when (was) {
                        1 -> {
                            velocity.computeCurrentVelocity(1000)
                            val speed = if (event.actionMasked == MotionEvent.ACTION_CANCEL) 0f else velocity.yVelocity
                            settleVertical(speed, event.rawY - downY)
                            true
                        }
                        2 -> {
                            settleHorizontal()
                            true
                        }
                        else -> {
                            // Tap with almost no drag — snap back to the current expand/collapse state.
                            if (sheetShown && sheet.visibility == View.VISIBLE) {
                                val target = if (scheduleExpanded) 0f else collapsedOffset.toFloat()
                                if (kotlin.math.abs(sheet.translationY - target) > 1.5f) {
                                    animateSheetY(target, scheduleExpanded)
                                }
                            }
                            false
                        }
                    }
                }
                else -> false
            }
        }
    }

    /** Chooses expand, collapse or hide based on drag distance and fling velocity. */
    private fun settleVertical(velocityY: Float, dragDy: Float) {
        val sheet = binding.stationSheet
        val y = sheet.translationY
        val peek = collapsedOffset.toFloat().coerceAtLeast(0f)
        // Drag far enough down (or fling down) to dismiss the sheet entirely.
        val hideAt = peek + (sheet.height - peek).coerceAtLeast(0f) * 0.22f
        when {
            y > hideAt || (velocityY >= 1400f && dragDy > 0f && y > peek + 12f) -> hideSheet()
            dragDy < 0f || velocityY <= -600f -> animateSheetY(0f, expanded = true)
            else -> animateSheetY(peek, expanded = false)
        }
    }

    /** Swipes to the next/previous nearby station when dragged far enough sideways. */
    private fun settleHorizontal() {
        val sheet = binding.stationSheet
        val index = nearbyOrder.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        val step = when {
            nearbyOrder.size <= 1 -> 0
            // Swipe left → next station; swipe right → previous.
            sheet.translationX < -sheet.width * 0.22f && index < nearbyOrder.lastIndex -> 1
            sheet.translationX > sheet.width * 0.22f && index > 0 -> -1
            else -> 0
        }
        if (step != 0) {
            val exitX = if (step > 0) -sheet.width.toFloat() else sheet.width.toFloat()
            val enterX = -exitX * 0.35f
            sheet.animate()
                .translationX(exitX)
                .setDuration(180)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    if (_binding == null) return@withEndAction
                    showNearby(step)
                    // Slide the new station content in from the opposite side.
                    sheet.translationX = enterX
                    sheet.animate()
                        .translationX(0f)
                        .setDuration(200)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                .start()
        } else {
            // Not far enough — spring back to centre.
            sheet.animate()
                .translationX(0f)
                .setDuration(180)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    /** Animates the sheet to fully expanded (Y=0) or collapsed peek height. */
    private fun animateSheetY(target: Float, expanded: Boolean) {
        val sheet = binding.stationSheet
        sheet.animate().cancel()
        scheduleExpanded = expanded
        sheetSettling = true
        sheet.animate()
            .translationY(target)
            .setDuration(360)
            .setInterpolator(android.view.animation.DecelerateInterpolator(1.4f))
            .setUpdateListener { updateHoursHint() }
            .withEndAction {
                if (_binding == null) return@withEndAction
                sheetSettling = false
                scheduleExpanded = expanded
                sheet.translationY = target
                updateHoursHint()
            }
            .start()
    }

    /** Slides the sheet off-screen and resets schedule peek state. */
    private fun hideSheet() {
        val sheet = binding.stationSheet
        sheet.animate().cancel()
        scheduleExpanded = false
        sheetSettling = true
        sheet.animate()
            .translationY(sheet.height.toFloat())
            .setDuration(300)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction {
                sheetSettling = false
                if (_binding == null) return@withEndAction
                sheet.visibility = View.INVISIBLE
                sheet.translationY = 0f
                sheet.translationX = 0f
                sheetShown = false
                scheduleExpanded = false
                collapsedOffset = 0
                binding.schedulePanel.visibility = View.GONE
                binding.tvSlideHours.alpha = 1f
            }
            .start()
    }

    /** Fades the "slide for hours" hint as the schedule panel comes into view. */
    private fun updateHoursHint() {
        if (_binding == null) return
        val peek = collapsedOffset.toFloat()
        binding.tvSlideHours.alpha = if (peek <= 1f) {
            if (scheduleExpanded) 0f else 1f
        } else {
            (binding.stationSheet.translationY / peek).coerceIn(0f, 1f)
        }
    }

    private fun topMargin(view: View): Int {
        val params = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
        return params.topMargin
    }

    /** Moves selection to the next/previous station in nearby order and pans the map. */
    private fun showNearby(step: Int) {
        val order = nearbyOrder
        if (order.isEmpty()) return
        val index = order.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        val next = order[(index + step).coerceIn(0, order.lastIndex)]
        showStation(next)
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(next.latitude, next.longitude), 15f)
        )
        markers.firstOrNull { it.second.id == next.id }?.first?.showInfoWindow()
    }

    /** Shows up to five name/address matches under the search box. */
    private fun showSuggestions(query: String) {
        val list = binding.suggestionList
        list.removeAllViews()
        val text = query.trim()
        val matches = if (text.isEmpty()) {
            emptyList()
        } else {
            markers.map { it.second }
                .filter {
                    it.name.contains(text, ignoreCase = true) ||
                        it.address.orEmpty().contains(text, ignoreCase = true)
                }
                .take(5)
        }
        if (matches.isEmpty()) {
            list.visibility = View.GONE
            return
        }
        val density = resources.displayMetrics.density
        val padH = (14 * density).toInt()
        val padV = (10 * density).toInt()
        matches.forEach { station ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(padH, padV, padH, padV)
                setOnClickListener { selectSuggestion(station) }
            }
            row.addView(TextView(requireContext()).apply {
                this.text = station.name
                setTextColor(0xFF0B1C30.toInt())
                textSize = 14f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            val address = station.address?.takeIf { it.isNotBlank() }
            if (address != null) {
                row.addView(TextView(requireContext()).apply {
                    this.text = address
                    setTextColor(0xFF4D4632.toInt())
                    textSize = 12f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
            list.addView(row)
        }
        list.visibility = View.VISIBLE
    }

    /** Applies a suggestion: fills search, opens the sheet and zooms to the station. */
    private fun selectSuggestion(station: SolarStation) {
        applyingSuggestion = true
        binding.etSearch.setText(station.name)
        binding.etSearch.setSelection(station.name.length)
        applyingSuggestion = false
        hideSuggestions()
        val input = context?.getSystemService(InputMethodManager::class.java)
        input?.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        showStation(station)
        googleMap?.animateCamera(
            CameraUpdateFactory.newLatLngZoom(LatLng(station.latitude, station.longitude), 15f)
        )
        markers.firstOrNull { it.second.id == station.id }?.first?.showInfoWindow()
    }

    private fun hideSuggestions() {
        if (_binding == null) return
        binding.suggestionList.removeAllViews()
        binding.suggestionList.visibility = View.GONE
    }

    /** Shows/hides markers whose name or address matches the search query. */
    private fun filterMarkers(query: String) {
        val text = query.trim().lowercase()
        markers.forEach { (marker, station) ->
            marker.isVisible = text.isEmpty() ||
                station.name.lowercase().contains(text) ||
                station.address.orEmpty().lowercase().contains(text)
        }
    }

    /** Haversine distance in kilometres between two map points. */
    private fun distanceKm(from: LatLng, to: LatLng): Double {
        val earth = 6371.0
        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) * sin(dLng / 2).pow(2)
        return 2 * earth * asin(sqrt(a))
    }

    companion object {
        // Default map centre when the user location is not yet available.
        private val COLOMBO = LatLng(6.9271, 79.8612)
    }
}
