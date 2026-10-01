package com.ead.solargrid.models

/**
 * GET /api/reservations/summary.
 * Scope is "All" for staff (operational counts) and "Own" for a prosumer.
 */
data class ReservationSummaryResponse(
    val message: String?,
    val scope: String?,
    val pendingCount: Long,
    val approvedFutureCount: Long
)

/** GET /api/reservations and /api/reservations/mine page. */
data class ReservationPageResponse(
    val message: String?,
    val items: List<ReservationItem>?,
    val page: Int,
    val pageSize: Int,
    val totalCount: Long,
    val totalPages: Int
)

data class ReservationItem(
    val id: String,
    val prosumerId: String,
    val stationId: String,
    val stationName: String?,
    val slotId: String,
    val status: String,
    val slotStartTimeUtc: String?,
    val slotEndTimeUtc: String?,
    val createdAtUtc: String? = null,
    /** Sent back on approve / reject so a stale list gets a 409 instead of overwriting a newer change. */
    val version: Long? = null
)

data class SolarStation(
    val id: String,
    val name: String,
    val address: String?,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val capacityKw: Double = 0.0,
    val batteryStorageSlots: Int = 0,
    val isActive: Boolean
)

data class StationSchedule(
    val id: String,
    val stationId: String,
    val day: String,
    val openingTime: String,
    val closingTime: String,
    val isAvailable: Boolean
)

data class EnergyBookingSlotDto(
    val id: String,
    val stationId: String,
    val startTimeUtc: String,
    val endTimeUtc: String,
    val maximumBookings: Int,
    val reservedBookings: Int,
    val isActive: Boolean = true
) {
    val remainingBookings: Int
        get() = (maximumBookings - reservedBookings).coerceAtLeast(0)
}

data class CreateSlotRequest(
    val startTimeUtc: String,
    val endTimeUtc: String,
    val maximumBookings: Int
)

data class CreateSlotResponse(
    val id: String
)

data class CreateReservationRequest(
    val slotId: String,
    val stationId: String?
)

data class UpdateReservationRequest(
    val slotId: String,
    val stationId: String?,
    val version: Long? = null
)

data class CancelReservationRequest(
    val version: Long? = null
)

data class CreateReservationResponse(
    val message: String?,
    val reservationId: String?,
    val reservation: ReservationItem?
)

data class NearbyStation(
    val station: SolarStation,
    val distanceKm: Double
)
