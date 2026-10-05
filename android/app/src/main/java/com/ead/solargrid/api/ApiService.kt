package com.ead.solargrid.api

import com.ead.solargrid.models.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<LoginResponse>

    @POST("api/auth/register/start")
    suspend fun registerStart(@Body request: RegisterStartRequest): Response<RegisterStartResponse>

    @POST("api/auth/register/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): Response<User>

    @POST("api/auth/register/resend-otp")
    suspend fun resendOtp(@Body request: ResendOtpRequest): Response<BaseResponse>

    @POST("api/auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<BaseResponse>

    @POST("api/auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<BaseResponse>

    @GET("api/users/{nic}")
    suspend fun getUser(@Path("nic") nic: String): Response<User>

    @PATCH("api/users/{nic}/profile")
    suspend fun updateProfile(
        @Path("nic") nic: String,
        @Body request: UpdateProfileRequest
    ): Response<User>

    @POST("api/users/{nic}/request-email-change")
    suspend fun requestEmailChange(
        @Path("nic") nic: String,
        @Body request: EmailChangeRequest
    ): Response<BaseResponse>

    @POST("api/users/{nic}/verify-email-change")
    suspend fun verifyEmailChange(
        @Path("nic") nic: String,
        @Body request: EmailVerifyRequest
    ): Response<BaseResponse>

    @POST("api/deactivation-requests/users/{nic}")
    suspend fun requestDeactivation(
        @Path("nic") nic: String,
        @Body body: CreateDeactivationRequest
    ): Response<BaseResponse>

    @GET("api/reservations/summary")
    suspend fun getReservationSummary(): Response<ReservationSummaryResponse>

    /** Staff reservation list. dateUtc is the UTC day of the slot start (yyyy-MM-dd). */
    /** q matches station name text or a full reservation id. */
    @GET("api/reservations")
    suspend fun getReservations(
        @Query("status") status: String? = null,
        @Query("dateUtc") dateUtc: String? = null,
        @Query("page") page: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("stationId") stationId: String? = null,
        @Query("q") query: String? = null
    ): Response<ReservationPageResponse>

    /** Staff only, for a Pending reservation whose slot has not started. */
    @PATCH("api/reservations/{id}/approve")
    suspend fun approveReservation(
        @Path("id") id: String,
        @Body body: ReservationActionRequest
    ): Response<ReservationActionResponse>

    /** Staff only, for a Pending reservation. Releases the slot capacity. */
    @PATCH("api/reservations/{id}/reject")
    suspend fun rejectReservation(
        @Path("id") id: String,
        @Body body: ReservationActionRequest
    ): Response<ReservationActionResponse>

    @GET("api/reservations/mine")
    suspend fun getMyReservations(
        @Query("status") status: String? = null,
        @Query("pageSize") pageSize: Int = 20,
        @Query("page") page: Int? = null
    ): Response<ReservationPageResponse>

    @GET("api/stations")
    suspend fun getStations(): Response<List<SolarStation>>

    @GET("api/stations/nearby")
    suspend fun getNearbyStations(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 50.0
    ): Response<List<NearbyStation>>

    @GET("api/stations/{id}")
    suspend fun getStation(@Path("id") id: String): Response<SolarStation>

    @GET("api/stations/{id}/schedules")
    suspend fun getStationSchedules(@Path("id") id: String): Response<List<StationSchedule>>

    @GET("api/stations/{id}/slots")
    suspend fun getStationSlots(
        @Path("id") id: String,
        @Query("dateUtc") dateUtc: String,
        @Query("includeFull") includeFull: Boolean = false
    ): Response<List<EnergyBookingSlotDto>>

    @POST("api/booking-slots/station/{stationId}")
    suspend fun createBookingSlot(
        @Path("stationId") stationId: String,
        @Body body: CreateSlotRequest
    ): Response<EnergyBookingSlotDto>

    @POST("api/reservations")
    suspend fun createReservation(@Body body: CreateReservationRequest): Response<CreateReservationResponse>

    @PUT("api/reservations/{id}")
    suspend fun updateReservation(
        @Path("id") id: String,
        @Body body: UpdateReservationRequest
    ): Response<CreateReservationResponse>

    @PATCH("api/reservations/{id}/cancel")
    suspend fun cancelReservation(
        @Path("id") id: String,
        @Body body: CancelReservationRequest
    ): Response<CreateReservationResponse>

    /** Prosumer only, for their own Approved reservation. */
    @GET("api/reservations/{id}/qr")
    suspend fun getReservationQr(@Path("id") id: String): Response<QrCodeResponse>

    /** Grid Operator only. Read-only check of a scanned QR string. */
    @POST("api/reservations/verify-qr")
    suspend fun verifyQr(@Body body: VerifyQrRequest): Response<QrVerificationResponse>

    /** Grid Operator only. A repeat call returns 409 "already completed". */
    @PATCH("api/reservations/{id}/complete")
    suspend fun completeReservation(
        @Path("id") id: String,
        @Body body: ReservationActionRequest
    ): Response<ReservationActionResponse>
}
