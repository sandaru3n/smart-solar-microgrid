/*
 * File: AuthInterceptor.kt
 * Description: Intercepts outgoing HTTP requests to append the JWT token and handles 401 Unauthorized responses for auto-logout.
 * Author: IT23163904_WVADK Chamara
 */
package com.ead.solargrid.api

import android.content.Context
import okhttp3.Interceptor
import okhttp3.Response
import com.ead.solargrid.database.SessionManager

class AuthInterceptor(private val context: Context) : Interceptor {
    // Injects the saved JWT token into the Authorization header of every outgoing API request.
    override fun intercept(chain: Interceptor.Chain): Response {
        val sessionManager = SessionManager(context)
        val token = sessionManager.fetchAuthToken()

        val requestBuilder = chain.request().newBuilder()

        if (token != null) {
            requestBuilder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 401) {
            // Kick the user out
            sessionManager.logout()

            // Show a Toast on the main thread
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                android.widget.Toast.makeText(
                    context, 
                    "Your account has been deactivated by the backoffice.", 
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }

            // Redirect to Login Page
            val intent = android.content.Intent(context, com.ead.solargrid.ui.auth.LoginActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(intent)
        }

        return response
    }
}
