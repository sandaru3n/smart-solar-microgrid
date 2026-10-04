package com.ead.solargrid.models

data class User(
    val id: String?,
    val nic: String,
    val name: String,
    val email: String,
    val phone: String?,
    val address: String?,
    val role: String,
    val accountStatus: String,
    val emailVerified: Boolean? = null,
    val nicVerificationStatus: String? = null,
    val profilePicUrl: String? = null
)
