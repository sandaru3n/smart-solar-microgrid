package com.ead.solargrid.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.R
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.models.ResetPasswordRequest
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ResetPasswordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        val email = intent.getStringExtra("EMAIL") ?: ""
        
        val etOtp = findViewById<TextInputEditText>(R.id.etOtp)
        val etNewPassword = findViewById<TextInputEditText>(R.id.etNewPassword)
        val btnResetPassword = findViewById<Button>(R.id.btnResetPassword)

        btnResetPassword.setOnClickListener {
            val otp = etOtp.text.toString().trim()
            val newPassword = etNewPassword.text.toString().trim()

            if (otp.isEmpty()) {
                etOtp.error = "OTP is required"
                return@setOnClickListener
            }
            if (newPassword.isEmpty()) {
                etNewPassword.error = "New password is required"
                return@setOnClickListener
            }

            btnResetPassword.isEnabled = false
            btnResetPassword.text = "Resetting..."

            lifecycleScope.launch {
                try {
                    val response = ApiClient.getApiService(this@ResetPasswordActivity).resetPassword(
                        ResetPasswordRequest(email, otp, newPassword)
                    )
                    if (response.isSuccessful) {
                        Toast.makeText(this@ResetPasswordActivity, "Password reset successfully", Toast.LENGTH_LONG).show()
                        val intent = Intent(this@ResetPasswordActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@ResetPasswordActivity, "Failed: ${response.message()}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@ResetPasswordActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    btnResetPassword.isEnabled = true
                    btnResetPassword.text = "Reset Password"
                }
            }
        }
    }
}
