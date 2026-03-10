package com.sidekick.opt_pal

import android.app.Application
import android.content.pm.ApplicationInfo
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.sidekick.opt_pal.di.AppModule

class OPTPalApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
        FirebaseApp.initializeApp(this)
        val isDebuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val providerFactory = if (isDebuggable) {
            DebugAppCheckProviderFactory.getInstance()
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(providerFactory)
        Firebase.crashlytics.setCrashlyticsCollectionEnabled(!isDebuggable)
        
        // Initialize secure vault & AI dependencies
        val geminiApiKey = BuildConfig.GEMINI_API_KEY
        if (geminiApiKey.isNotBlank()) {
            com.sidekick.opt_pal.di.AppDependencies.initialize(this, geminiApiKey)
        } else {
            android.util.Log.w("OPTPalApp", "Gemini API key not found. Add to local.properties: GEMINI_API_KEY=your_key")
        }
    }
}
