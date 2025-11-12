package com.unh.hoppin_android_app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")