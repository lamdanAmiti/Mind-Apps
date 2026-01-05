package com.mindapps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mind_apps_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private val SETUP_COMPLETED = booleanPreferencesKey("setup_completed")
        private val UPDATE_NOTIFICATIONS_ENABLED = booleanPreferencesKey("update_notifications_enabled")
        private val LIBRARY_APP_IDS = stringSetPreferencesKey("library_app_ids")
    }

    val isSetupCompleted: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SETUP_COMPLETED] ?: false
    }

    val isUpdateNotificationsEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[UPDATE_NOTIFICATIONS_ENABLED] ?: true
    }

    val libraryAppIds: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        preferences[LIBRARY_APP_IDS] ?: emptySet()
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

    suspend fun setLibraryAppIds(appIds: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[LIBRARY_APP_IDS] = appIds
        }
    }

    suspend fun addToLibrary(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[LIBRARY_APP_IDS] ?: emptySet()
            preferences[LIBRARY_APP_IDS] = current + packageName
        }
    }

    suspend fun removeFromLibrary(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[LIBRARY_APP_IDS] ?: emptySet()
            preferences[LIBRARY_APP_IDS] = current - packageName
        }
    }
}
