package com.mindapps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mind_apps_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        private val UPDATE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("update_notifications_enabled")
    }

    val isSetupCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SETUP_COMPLETED] ?: false
    }

    val isUpdateNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[UPDATE_NOTIFICATIONS_ENABLED] ?: true
    }

    suspend fun setSetupCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SETUP_COMPLETED] = completed
        }
    }

    suspend fun setUpdateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[UPDATE_NOTIFICATIONS_ENABLED] = enabled
        }
    }
}
