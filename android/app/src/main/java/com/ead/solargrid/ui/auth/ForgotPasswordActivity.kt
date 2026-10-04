package com.ead.solargrid.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.R
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.models.ForgotPasswordRequest
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val btnSendOtp = findViewById<Button>(R.id.btnSendOtp)

        btnSendOtp.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (email.isEmpty()) {
                etEmail.error = "Email is required"
                return@setOnClickListener
            }

            btnSendOtp.isEnabled = false
            btnSendOtp.text = "Sending..."

            lifecycleScope.launch {
                try {
                    val response = ApiClient.getApiService(this@ForgotPasswordActivity).forgotPassword(ForgotPasswordRequest(email))
                    if (response.isSuccessful) {
                        Toast.makeText(this@ForgotPasswordActivity, "OTP sent to your email", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@ForgotPasswordActivity, ResetPasswordActivity::class.java)
                        intent.putExtra("EMAIL", email)
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@ForgotPasswordActivity, "Failed: ${response.message()}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ForgotPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    btnSendOtp.isEnabled = true
                    btnSendOtp.text = "Send OTP"
                }
            }
        }
    }
}
