package com.trailerly.util

import timber.log.Timber
import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.util.Log

/**
 * Custom Timber Tree for release builds that integrates with Firebase Crashlytics.
 * Filters out debug/verbose logs for performance and forwards warnings/errors to Crashlytics.
 * Debug builds use Timber.DebugTree instead for full logging to Logcat.
 */
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Only process warnings and errors in release builds for performance
        if (priority != Log.WARN && priority != Log.ERROR) {
            return
        }

        // Format message with tag for better context
        val formattedMessage = if (tag != null) "[$tag] $message" else message

        // Add breadcrumb to Crashlytics
        crashlytics.log(formattedMessage)

        // Record exception if provided, or create synthetic exception for errors
        if (t != null) {
            crashlytics.recordException(t)
        } else if (priority == Log.ERROR) {
            // For error logs without explicit exceptions, create a synthetic one
            crashlytics.recordException(Exception(formattedMessage))
        }
    }
}
