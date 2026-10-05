package com.ead.solargrid.ui.home

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.ead.solargrid.api.ApiClient
import com.ead.solargrid.database.SessionManager
import com.ead.solargrid.databinding.DialogLogoutConfirmBinding
import com.ead.solargrid.databinding.FragmentProsumerProfileBinding
import com.ead.solargrid.models.User
import com.ead.solargrid.ui.auth.LoginActivity
import kotlinx.coroutines.launch
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProsumerProfileBinding? = null
    private val binding get() = _binding!!
    private var currentEditImageView: android.widget.ImageView? = null
    
    private val pickImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            uploadProfilePic(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val content = FragmentProsumerProfileBinding.inflate(inflater, container, false)
        _binding = content
        return content.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val session = SessionManager(requireContext())
        session.getUserSession()?.let { showUser(it) }

        binding.btnLogout.setOnClickListener { confirmLogout(session) }

        val nic = session.getUserSession()?.nic ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService(requireContext()).getUser(nic)
                val user = response.body()
                if (!response.isSuccessful || user == null || _binding == null) return@launch
                session.saveUserSession(
                    nic = user.nic,
                    name = user.name,
                    email = user.email,
                    phone = user.phone.orEmpty(),
                    address = user.address.orEmpty(),
                    role = user.role,
                    accountStatus = user.accountStatus,
                    profilePicUrl = user.profilePicUrl
                )
                showUser(user)
            } catch (_: Exception) {
                // Keep the details already stored on this phone.
            }
        }
    }

    private fun confirmLogout(session: SessionManager) {
        val dialogBinding = DialogLogoutConfirmBinding.inflate(layoutInflater)
        val dialog = Dialog(requireContext())
        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.88f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialogBinding.btnStaySignedIn.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmLogout.setOnClickListener {
            dialog.dismiss()
            session.logout()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
        dialog.setOnDismissListener {
            currentEditImageView = null
        }
        dialog.show()
    }

    private fun showUser(user: User) {
        binding.tvProfileName.text = user.name.ifBlank { "—" }
        binding.tvProfileNic.text = user.nic.ifBlank { "—" }
        binding.tvProfileEmail.text = user.email.ifBlank { "—" }
        binding.tvProfilePhone.text = user.phone?.ifBlank { "—" } ?: "—"
        binding.tvProfileAddress.text = user.address?.ifBlank { "—" } ?: "—"
        binding.tvProfileStatus.text = pretty(user.accountStatus)
        binding.tvProfileNicStatus.text = pretty(user.nicVerificationStatus)
        
        val session = SessionManager(requireContext())
        
        binding.btnEditProfile.setOnClickListener {
            showEditProfileDialog(user, session)
        }
        
        // Load profile picture if available
        user.profilePicUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            val imageView = binding.root.findViewById<android.widget.ImageView>(com.ead.solargrid.R.id.ivProfilePic)
            if (imageView != null) {
                com.bumptech.glide.Glide.with(requireContext())
                    .load(url)
                    .circleCrop()
                    .into(imageView)
            }
        }

        binding.btnDeactivate.setOnClickListener {
            requestDeactivation(user.nic)
        }
    }

    private fun pretty(value: String?): String {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return "—"
        return raw.lowercase(Locale.getDefault())
            .split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }
    }

    private fun showEditProfileDialog(user: com.ead.solargrid.models.User?, session: SessionManager) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(com.ead.solargrid.R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.ead.solargrid.R.id.etEditName)
        val etEmail = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.ead.solargrid.R.id.etEditEmail)
        val etPhone = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.ead.solargrid.R.id.etEditPhone)
        val etAddress = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.ead.solargrid.R.id.etEditAddress)

        etName.setText(user?.name)
        etEmail.setText(user?.email)
        etPhone.setText(user?.phone)
        etAddress.setText(user?.address)

        val btnChangePic = dialogView.findViewById<android.widget.Button>(com.ead.solargrid.R.id.btnChangePic)
        val ivEditPic = dialogView.findViewById<android.widget.ImageView>(com.ead.solargrid.R.id.ivEditProfilePic)
        
        currentEditImageView = ivEditPic
        user?.profilePicUrl?.takeIf { it.isNotEmpty() }?.let { url ->
            com.bumptech.glide.Glide.with(requireContext())
                .load(url)
                .circleCrop()
                .into(ivEditPic)
        }

        btnChangePic.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newEmail = etEmail.text.toString().trim()
                val request = com.ead.solargrid.models.UpdateProfileRequest(
                    name = null,
                    email = null, // Handled separately
                    phone = etPhone.text.toString().trim(),
                    address = etAddress.text.toString().trim(),
                    profilePicUrl = null
                )
                
                if (newEmail.isNotEmpty() && newEmail != user?.email) {
                    initiateEmailChange(user?.nic, newEmail, request, session)
                } else {
                    updateProfile(user?.nic, request, session)
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnDismissListener {
            currentEditImageView = null
        }
        dialog.show()
    }

    private fun initiateEmailChange(nic: String?, newEmail: String, pendingRequest: com.ead.solargrid.models.UpdateProfileRequest, session: SessionManager) {
        if (nic == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val response = api.requestEmailChange(nic, com.ead.solargrid.models.EmailChangeRequest(newEmail))
                if (response.isSuccessful) {
                    showEmailOtpDialog(nic, pendingRequest, session)
                } else {
                    val err = response.errorBody()?.string() ?: "Failed to request email change"
                    android.widget.Toast.makeText(requireContext(), err, android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showEmailOtpDialog(nic: String, pendingRequest: com.ead.solargrid.models.UpdateProfileRequest, session: SessionManager) {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext())
        input.hint = "Enter OTP sent to new email"
        input.inputType = android.text.InputType.TYPE_CLASS_NUMBER
        val layout = android.widget.FrameLayout(requireContext())
        layout.setPadding(60, 40, 60, 0)
        layout.addView(input)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Verify New Email")
            .setMessage("Please enter the OTP sent to your new email address.")
            .setView(layout)
            .setPositiveButton("Verify") { _, _ ->
                val otp = input.text.toString().trim()
                verifyEmailChangeAndSave(nic, otp, pendingRequest, session)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyEmailChangeAndSave(nic: String, otp: String, pendingRequest: com.ead.solargrid.models.UpdateProfileRequest, session: SessionManager) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.getApiService(requireContext())
                val response = api.verifyEmailChange(nic, com.ead.solargrid.models.EmailVerifyRequest(otp))
                if (response.isSuccessful) {
                    android.widget.Toast.makeText(requireContext(), "Email verified successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    updateProfile(nic, pendingRequest, session)
                } else {
                    val err = response.errorBody()?.string() ?: "Invalid OTP"
                    android.widget.Toast.makeText(requireContext(), err, android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private var isUploading = false
    private fun uploadProfilePic(uri: android.net.Uri) {
        if (isUploading) return
        isUploading = true
        val nic = SessionManager(requireContext()).getUserSession()?.nic ?: return
        
        android.widget.Toast.makeText(requireContext(), "Uploading Profile Picture...", android.widget.Toast.LENGTH_SHORT).show()
        
        com.cloudinary.android.MediaManager.get().upload(uri).callback(object : com.cloudinary.android.callback.UploadCallback {
            override fun onStart(requestId: String) {}
            override fun onProgress(requestId: String, bytes: Long, totalBytes: Long) {}
            override fun onSuccess(requestId: String, resultData: Map<*, *>) {
                val secureUrl = resultData["secure_url"] as String
                updateProfile(nic, com.ead.solargrid.models.UpdateProfileRequest(null, null, null, null, secureUrl), SessionManager(requireContext()))
                isUploading = false
            }
            override fun onError(requestId: String, error: com.cloudinary.android.callback.ErrorInfo) {
                activity?.runOnUiThread {
                    android.widget.Toast.makeText(requireContext(), "Upload failed: ${error.description}", android.widget.Toast.LENGTH_SHORT).show()
                }
                isUploading = false
            }
            override fun onReschedule(requestId: String, error: com.cloudinary.android.callback.ErrorInfo) {}
        }).dispatch()
    }

    private fun updateProfile(nic: String?, request: com.ead.solargrid.models.UpdateProfileRequest, session: SessionManager) {
        if (nic == null) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService(requireContext()).updateProfile(nic, request)
                if (response.isSuccessful) {
                    val updatedUser = response.body()
                    if (updatedUser != null && _binding != null) {
                        session.saveUserSession(
                            nic = updatedUser.nic,
                            name = updatedUser.name,
                            email = updatedUser.email,
                            phone = updatedUser.phone.orEmpty(),
                            address = updatedUser.address.orEmpty(),
                            role = updatedUser.role,
                            accountStatus = updatedUser.accountStatus,
                            profilePicUrl = updatedUser.profilePicUrl
                        )
                        showUser(updatedUser)
                        
                        // Also update the dialog image if it's open
                        currentEditImageView?.let { iv ->
                            updatedUser.profilePicUrl?.takeIf { it.isNotEmpty() }?.let { url ->
                                com.bumptech.glide.Glide.with(requireContext())
                                    .load(url)
                                    .circleCrop()
                                    .into(iv)
                            }
                        }

                        android.widget.Toast.makeText(requireContext(), "Profile Updated", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    android.widget.Toast.makeText(requireContext(), "Update Failed", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestDeactivation(nic: String?) {
        if (nic == null) return
        
        val input = android.widget.EditText(requireContext())
        input.hint = "Reason for deactivation (min 10 chars)"
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        )
        input.layoutParams = lp
        
        val container = android.widget.LinearLayout(requireContext())
        container.setPadding(50, 20, 50, 0)
        container.addView(input)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Request Deactivation")
            .setMessage("Are you sure you want to deactivate your account? This action requires backoffice approval.")
            .setView(container)
            .setPositiveButton("Submit") { _, _ ->
                val reason = input.text.toString()
                if (reason.length < 10) {
                    android.widget.Toast.makeText(requireContext(), "Reason must be at least 10 characters.", android.widget.Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val body = com.ead.solargrid.models.CreateDeactivationRequest(reason)
                        val response = ApiClient.getApiService(requireContext()).requestDeactivation(nic, body)
                        if (response.isSuccessful && _binding != null) {
                            android.widget.Toast.makeText(requireContext(), "Deactivation Requested", android.widget.Toast.LENGTH_SHORT).show()
                            binding.tvProfileStatus.text = pretty("PENDING_DEACTIVATION")
                        } else {
                            android.widget.Toast.makeText(requireContext(), "Request Failed: Already pending?", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
