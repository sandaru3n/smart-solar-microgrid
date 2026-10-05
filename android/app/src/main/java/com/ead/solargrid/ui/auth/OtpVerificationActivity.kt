/*
 * File: OtpVerificationActivity.kt
 * Description: Provides the UI and logic for verifying the registration OTP during Prosumer onboarding.
 * Author: IT23163904_WVADK Chamara
 */
package com.ead.solargrid.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.R
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.ui.SystemBarUtils
import com.ead.solargrid.models.ResendOtpRequest
import com.ead.solargrid.models.VerifyOtpRequest
import kotlinx.coroutines.launch

class OtpVerificationActivity : AppCompatActivity() {

    private var registrationId: String? = null

    // Initializes the OTP verification view, setting up the API call for verification and the resend action.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_verification)
        val root = findViewById<android.view.View>(R.id.activityRoot)
        val content = findViewById<android.view.View>(R.id.activityContent)
        val scrim = findViewById<android.view.View>(R.id.statusBarScrim)
        SystemBarUtils.applyInsetsOnContent(this, root, content, scrim)

        registrationId = intent.getStringExtra("REGISTRATION_ID")

        if (registrationId == null) {
            Toast.makeText(this, "Session invalid. Please register again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val etOtp = findViewById<EditText>(R.id.etOtp)
        val btnVerify = findViewById<Button>(R.id.btnVerify)
        val btnResend = findViewById<Button>(R.id.btnResend)

        btnVerify.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            if (otp.isEmpty() || otp.length != 6) {
                Toast.makeText(this, "Please enter a valid 6-digit OTP", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val api = ApiClient.getApiService(this@OtpVerificationActivity)
                    val response = api.verifyOtp(VerifyOtpRequest(registrationId!!, otp))

                    if (response.isSuccessful && response.body() != null) {
                        Toast.makeText(this@OtpVerificationActivity, "Registration complete! Awaiting Backoffice approval.", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@OtpVerificationActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    } else {
                        Toast.makeText(this@OtpVerificationActivity, "Invalid OTP or Expired", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@OtpVerificationActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        btnResend.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val api = ApiClient.getApiService(this@OtpVerificationActivity)
                    val response = api.resendOtp(ResendOtpRequest(registrationId!!))
                    if (response.isSuccessful) {
                        Toast.makeText(this@OtpVerificationActivity, "OTP Resent", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@OtpVerificationActivity, "Please wait before resending", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@OtpVerificationActivity, "Network Error", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnBack = findViewById<Button>(R.id.btnBack)
        btnBack.setOnClickListener {
            finish()
        }
    }
}
