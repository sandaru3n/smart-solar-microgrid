package com.ead.solargrid.database

import android.content.Context
import android.content.SharedPreferences
import com.ead.solargrid.models.User

class SessionManager(context: Context) {
    private var prefs: SharedPreferences = context.getSharedPreferences("solar_grid_prefs", Context.MODE_PRIVATE)
    private val dbHelper = DatabaseHelper(context)

    companion object {
        const val USER_TOKEN = "user_token"
    }

    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(USER_TOKEN, token)
        editor.apply()
    }

    fun fetchAuthToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    fun saveUserSession(nic: String, name: String, email: String, phone: String, address: String, role: String, accountStatus: String, profilePicUrl: String?) {
        dbHelper.saveUser(nic, name, email, phone, address, role, accountStatus, profilePicUrl)
    }

    fun getUserSession(): User? {
        return dbHelper.getUser()
    }
    
    fun getRole(): String? {
        return dbHelper.getUserRole()
    }

    fun logout() {
        val editor = prefs.edit()
        editor.remove(USER_TOKEN)
        editor.apply()
        dbHelper.clearSession()
    }
}
