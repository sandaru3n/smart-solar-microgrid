package com.ead.solargrid.ui.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
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
import com.ead.solargrid.ui.SystemBarUtils
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class RegisterActivity : AppCompatActivity() {

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

    private val PICK_FILE_REQUEST = 1
    private var selectedFileUri: Uri? = null
    private lateinit var tvDocName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        val root = findViewById<android.view.View>(R.id.activityRoot)
        val content = findViewById<android.view.View>(R.id.activityContent)
        val scrim = findViewById<android.view.View>(R.id.statusBarScrim)
        SystemBarUtils.applyInsetsOnContent(this, root, content, scrim)

        val etName = findViewById<EditText>(R.id.etName)
        val etNic = findViewById<EditText>(R.id.etNic)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPhone = findViewById<EditText>(R.id.etPhone)
        val etAddress = findViewById<EditText>(R.id.etAddress)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val etConfirmPassword = findViewById<EditText>(R.id.etConfirmPassword)
        val btnSelectDoc = findViewById<Button>(R.id.btnSelectDoc)
        tvDocName = findViewById(R.id.tvDocName)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        findViewById<TextView>(R.id.tvGoLogin).setOnClickListener { finish() }
        wirePasswordToggle(etPassword, findViewById(R.id.btnTogglePassword))
        wirePasswordToggle(etConfirmPassword, findViewById(R.id.btnToggleConfirmPassword))

        btnSelectDoc.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            val mimeTypes = arrayOf("image/jpeg", "image/png", "application/pdf")
            intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
            startActivityForResult(intent, PICK_FILE_REQUEST)
        }

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val nic = etNic.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()
            val phone = etPhone.text.toString().trim()
            val address = etAddress.text.toString().trim()

            if (name.isEmpty() || nic.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Name, NIC, Email, and Password are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirm) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val pwdRegex = Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,15}$")
            if (!pwdRegex.matches(password)) {
                Toast.makeText(this, "Password must be 8-15 characters long, contain at least one uppercase letter, one lowercase letter, and one number.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val nicRegex = "^(?:[0-9]{9}[VvXx]|[0-9]{12})$"
            if (!Pattern.matches(nicRegex, nic)) {
                Toast.makeText(this, "Invalid Sri Lankan NIC format", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedFileUri == null) {
                Toast.makeText(this, "Please select your NIC document", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            submitRegistration(name, nic, email, password, phone, address)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            selectedFileUri = data.data
            tvDocName.text = getFileName(selectedFileUri!!)
        }
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "document"
    }

    private fun submitRegistration(name: String, nic: String, email: String, pass: String, phone: String, address: String) {
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        btnRegister.isEnabled = false
        btnRegister.text = "Uploading Document..."
        Toast.makeText(this, "Uploading NIC to Cloudinary...", Toast.LENGTH_SHORT).show()

        com.cloudinary.android.MediaManager.get().upload(selectedFileUri!!)
            .callback(object : com.cloudinary.android.callback.UploadCallback {
                override fun onStart(requestId: String) {}

                override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}

                override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                    val secureUrl = resultData["secure_url"] as String
                    runOnUiThread {
                        btnRegister.text = "Sending OTP..."
                        Toast.makeText(this@RegisterActivity, "Upload complete. Registering...", Toast.LENGTH_SHORT).show()
                    }
                    sendRegistrationToBackend(name, nic, email, pass, phone, address, secureUrl)
                }

                override fun onError(requestId: String, error: com.cloudinary.android.callback.ErrorInfo) {
                    runOnUiThread {
                        btnRegister.isEnabled = true
                        btnRegister.text = "Submit Registration"
                        Toast.makeText(this@RegisterActivity, "Upload failed: ${error.description}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onReschedule(requestId: String, error: com.cloudinary.android.callback.ErrorInfo) {}
            }).dispatch()
    }

    private fun sendRegistrationToBackend(name: String, nic: String, email: String, pass: String, phone: String, address: String, nicUrl: String) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(this@RegisterActivity)
                val request = com.ead.solargrid.models.RegisterStartRequest(
                    nic = nic,
                    name = name,
                    email = email,
                    password = pass,
                    phone = phone,
                    address = address,
                    nicImageUrl = nicUrl
                )
                
                val response = api.registerStart(request)
                
                val btnRegister = findViewById<Button>(R.id.btnRegister)
                
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    Toast.makeText(this@RegisterActivity, body.message, Toast.LENGTH_LONG).show()
                    
                    val intent = Intent(this@RegisterActivity, OtpVerificationActivity::class.java)
                    intent.putExtra("REGISTRATION_ID", body.registrationId)
                    startActivity(intent)
                    finish()
                } else {
                    btnRegister.isEnabled = true
                    btnRegister.text = "Submit Registration"
                    val errorString = response.errorBody()?.string()
                    val errorMessage = try {
                        if (errorString != null) {
                            org.json.JSONObject(errorString).getString("message")
                        } else {
                            "Registration failed"
                        }
                    } catch (e: Exception) {
                        "Registration failed: ${response.message()}"
                    }
                    Toast.makeText(this@RegisterActivity, errorMessage, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                val btnRegister = findViewById<Button>(R.id.btnRegister)
                btnRegister.isEnabled = true
                btnRegister.text = "Submit Registration"
                Toast.makeText(this@RegisterActivity, "Network Error. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
