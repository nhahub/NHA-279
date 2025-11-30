package com.trailerly.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

/**
 * Extension property for Context to provide a type-safe way to access the app's DataStore instance.
 * The DataStore is created lazily on first access.
 */
val Context.dataStorePreferences: DataStore<Preferences> by preferencesDataStore(name = "trailerly_preferences")
