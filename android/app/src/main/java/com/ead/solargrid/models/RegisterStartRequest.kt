package com.ead.solargrid.models

data class RegisterStartRequest(
    val nic: String,
    val name: String,
    val email: String,
    val password: String,
    val phone: String,
    val address: String,
    val nicImageUrl: String
)
