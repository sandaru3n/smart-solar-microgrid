/*
 * File: SessionManager.kt
 * Description: Manages the Android user session by persisting the authentication token via SharedPreferences and profile data via SQLite.
 * Author: IT23163904_WVADK Chamara
 */
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

    // Saves the JWT authentication token securely in SharedPreferences.
    fun saveAuthToken(token: String) {
        val editor = prefs.edit()
        editor.putString(USER_TOKEN, token)
        editor.apply()
    }

    // Retrieves the currently saved JWT authentication token.
    fun fetchAuthToken(): String? {
        return prefs.getString(USER_TOKEN, null)
    }

    // Stores the authenticated user's profile details locally into the SQLite database.
    fun saveUserSession(nic: String, name: String, email: String, phone: String, address: String, role: String, accountStatus: String, profilePicUrl: String?) {
        dbHelper.saveUser(nic, name, email, phone, address, role, accountStatus, profilePicUrl)
    }

    // Fetches the user profile from the SQLite database.
    fun getUserSession(): User? {
        return dbHelper.getUser()
    }
    
    // Fetches only the user's role string from the SQLite database.
    fun getRole(): String? {
        return dbHelper.getUserRole()
    }

    // Clears the SharedPreferences token and SQLite database upon user logout.
    fun logout() {
        val editor = prefs.edit()
        editor.remove(USER_TOKEN)
        editor.apply()
        dbHelper.clearSession()
    }
}
