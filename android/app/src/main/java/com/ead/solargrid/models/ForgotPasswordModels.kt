package com.ead.solargrid.models

data class ForgotPasswordRequest(val email: String)

data class ResetPasswordRequest(
    val email: String,
    val otp: String,
    val newPassword: String
)
