package com.ead.solargrid.models

data class UpdateProfileRequest(
    val name: String?,
    val email: String?,
    val phone: String?,
    val address: String?,
    val profilePicUrl: String? = null
)

data class EmailChangeRequest(
    val newEmail: String
)

data class EmailVerifyRequest(
    val otp: String
)
