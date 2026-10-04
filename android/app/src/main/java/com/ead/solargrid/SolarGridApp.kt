package com.ead.solargrid

import android.app.Application
import com.cloudinary.android.MediaManager

class SolarGridApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        try {
            MediaManager.init(this, mapOf(
                "cloud_name" to BuildConfig.CLOUDINARY_CLOUD_NAME,
                "api_key" to BuildConfig.CLOUDINARY_API_KEY,
                "api_secret" to BuildConfig.CLOUDINARY_API_SECRET
            ))
        } catch (e: Exception) {
            // Already initialized or initialization failed
            e.printStackTrace()
        }
    }
}
