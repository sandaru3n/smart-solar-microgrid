package com.ead.solargrid.ui.operator.stations

import android.content.Context
import com.ead.solargrid.R
import java.text.DecimalFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formatting shared by the operator station screens. */
object StationFormat {

    /** Same zone ReservationUi shows slot times in. */
    val ZONE: ZoneId = ZoneId.of("Asia/Colombo")

    private val kwFormat = DecimalFormat("#,##0.#")
    /** Built per call so a locale change while the app runs is picked up. */
    private val clockFormat get() = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

    fun kw(context: Context, value: Double): String =
        context.getString(R.string.operator_value_kw, kwFormat.format(value))

    fun batterySlots(context: Context, count: Int): String =
        context.resources.getQuantityString(R.plurals.op_station_battery_slots, count, count)

    /** "08:00:00" (a .NET TimeSpan) -> "8:00 AM"; anything unparseable is shown as sent. */
    fun clock(value: String): String =
        try {
            LocalTime.parse(value).format(clockFormat)
        } catch (_: Exception) {
            value
        }

    /** Monday-first week, matching the API's DayOfWeek names ("Monday" ...). */
    val WEEK: List<DayOfWeek> = DayOfWeek.entries
}
