package com.trailerly

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.trailerly.BuildConfig
import com.trailerly.util.CrashlyticsTree
import com.trailerly.util.NetworkMonitor
import timber.log.Timber
import kotlinx.coroutines.launch

@HiltAndroidApp
class MovieApplication : Application() {

    companion object {
        lateinit var crashlytics: FirebaseCrashlytics
            private set
    }

    override fun onCreate() {
        super.onCreate()

        crashlytics = FirebaseCrashlytics.getInstance()
        
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        Timber.plant(CrashlyticsTree())
        
        NetworkMonitor.initialize(this)
        
        crashlytics.log("MovieApplication onCreate called")
        Timber.d("MovieApplication initialized")
        
        ProcessLifecycleOwner.get().lifecycleScope.launch {
            Timber.d("App moved to foreground")
        }
    }
}
