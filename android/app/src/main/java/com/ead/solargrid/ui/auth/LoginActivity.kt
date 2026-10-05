package com.ead.solargrid.ui.auth

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import kotlin.math.cos
import kotlin.math.sin
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.R
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.models.LoginRequest
import com.ead.solargrid.ui.SystemBarUtils
import com.ead.solargrid.ui.home.ProsumerHomeActivity
import com.ead.solargrid.ui.operator.GridOperatorHomeActivity
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val waveAnimators = mutableListOf<Animator>()

    private fun wirePasswordToggle(field: EditText, button: ImageButton) {
        button.setOnClickListener {
            val hidden = field.transformationMethod is PasswordTransformationMethod
            field.transformationMethod = if (hidden) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            button.setImageResource(if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility)
            field.setSelection(field.text?.length ?: 0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        val root = findViewById<android.view.View>(R.id.activityRoot)
        val content = findViewById<android.view.View>(R.id.activityContent)
        val scrim = findViewById<android.view.View>(R.id.statusBarScrim)
        SystemBarUtils.applyInsetsOnContent(this, root, content, scrim)
        startSunRayAnimation(findViewById(R.id.loginRays))

        val etNic = findViewById<EditText>(R.id.etNic)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        wirePasswordToggle(etPassword, findViewById(R.id.btnTogglePassword))
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        val cbRememberMe = findViewById<android.widget.CheckBox>(R.id.cbRememberMe)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        val prefs = getSharedPreferences("LoginPrefs", android.content.Context.MODE_PRIVATE)
        val savedNic = prefs.getString("saved_nic", "")
        val savedPass = prefs.getString("saved_pass", "")
        if (savedNic!!.isNotEmpty() && savedPass!!.isNotEmpty()) {
            etNic.setText(savedNic)
            etPassword.setText(savedPass)
            cbRememberMe.isChecked = true
        }

        val sessionManager = SessionManager(this)

        btnLogin.setOnClickListener {
            val nic = etNic.text.toString().trim()
            val password = etPassword.text.toString()

            if (nic.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val api = ApiClient.getApiService(this@LoginActivity)
                    val response = api.login(LoginRequest(nic, password))

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!
                        
                        if (body.accountStatus == "UNVERIFIED") {
                            Toast.makeText(this@LoginActivity, body.message, Toast.LENGTH_LONG).show()
                            val intent = Intent(this@LoginActivity, OtpVerificationActivity::class.java)
                            intent.putExtra("REGISTRATION_ID", body.registrationId)
                            startActivity(intent)
                            return@launch
                        }

                        if (body.role == "BACKOFFICE") {
                            Toast.makeText(this@LoginActivity, "Backoffice users must use the Web App.", Toast.LENGTH_LONG).show()
                            return@launch
                        }

                        if (cbRememberMe.isChecked) {
                            prefs.edit()
                                .putString("saved_nic", nic)
                                .putString("saved_pass", password)
                                .apply()
                        } else {
                            prefs.edit().clear().apply()
                        }

                        sessionManager.saveAuthToken(body.token)
                        sessionManager.saveUserSession(
                            nic = body.nic,
                            name = body.name,
                            email = "", // Fetch later or add to response
                            phone = "",
                            address = "",
                            role = body.role,
                            accountStatus = body.accountStatus,
                            profilePicUrl = null // Fetch details again in MainActivity
                        )

                        if (body.role == "PROSUMER") {
                            startActivity(Intent(this@LoginActivity, ProsumerHomeActivity::class.java))
                            finish()
                        } else if (body.role == "GRID_OPERATOR") {
                            startActivity(Intent(this@LoginActivity, GridOperatorHomeActivity::class.java))
                            finish()
                        }
                    } else {
                        val errorString = response.errorBody()?.string()
                        val errorMessage = try {
                            if (errorString != null) {
                                org.json.JSONObject(errorString).getString("message")
                            } else {
                                "Login failed"
                            }
                        } catch (e: Exception) {
                            "Login failed: ${response.message()}"
                        }
                        Toast.makeText(this@LoginActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            // startActivity(Intent(this, ForgotPasswordActivity::class.java))
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun startSunRayAnimation(rays: FrameLayout) {
        rays.post {
            if (isDestroyed || rays.width == 0) return@post
            val density = resources.displayMetrics.density
            val rayW = (7f * density).toInt()
            val center = rays.width / 2f
            val count = 16
            repeat(count) { index ->
                val rayH = ((if (index % 2 == 0) 32f else 24f) * density).toInt()
                val degrees = index * (360f / count)
                val radians = Math.toRadians(degrees.toDouble())
                val orbit = center + rayH / 2f - 8f * density
                val ray = View(this).apply {
                    background = ContextCompat.getDrawable(this@LoginActivity, R.drawable.bg_login_ray)
                    rotation = degrees + 90f
                }
                val x = center + orbit * cos(radians).toFloat() - rayW / 2f
                val y = center + orbit * sin(radians).toFloat() - rayH / 2f
                rays.addView(ray, FrameLayout.LayoutParams(rayW, rayH).apply {
                    leftMargin = x.toInt()
                    topMargin = y.toInt()
                })
            }
            val spin = ObjectAnimator.ofFloat(rays, View.ROTATION, 0f, 360f).apply {
                duration = 14000L
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
            waveAnimators.add(spin)
        }
    }

    override fun onDestroy() {
        waveAnimators.forEach { it.cancel() }
        super.onDestroy()
    }
}
