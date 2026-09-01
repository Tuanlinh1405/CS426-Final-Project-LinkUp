package com.example.linkup.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_FULL_NAME = stringPreferencesKey("user_full_name")
    }

    val tokenFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_ACCESS_TOKEN]
    }

    val userIdFlow: Flow<String?> = dataStore.data.map { preferences ->
        preferences[KEY_USER_ID]
    }

    suspend fun getStoredToken(): String? {
        return tokenFlow.firstOrNull()
    }

    suspend fun saveAuthData(
        token: String,
        userId: String,
        email: String,
        username: String,
        fullName: String?
    ) {
        dataStore.edit { preferences ->
            preferences[KEY_ACCESS_TOKEN] = token
            preferences[KEY_USER_ID] = userId
            preferences[KEY_USER_EMAIL] = email
            preferences[KEY_USER_NAME] = username
            if (fullName != null) {
                preferences[KEY_USER_FULL_NAME] = fullName
            } else {
                preferences.remove(KEY_USER_FULL_NAME)
            }
        }
    }

    suspend fun clearAuthData() {
        dataStore.edit { preferences ->
            preferences.remove(KEY_ACCESS_TOKEN)
            preferences.remove(KEY_USER_ID)
            preferences.remove(KEY_USER_EMAIL)
            preferences.remove(KEY_USER_NAME)
            preferences.remove(KEY_USER_FULL_NAME)
        }
    }
}
