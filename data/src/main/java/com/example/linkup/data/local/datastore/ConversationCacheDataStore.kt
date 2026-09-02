package com.example.linkup.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.linkup.data.remote.dto.ConversationDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the last conversation list per user so the chat screen can render it
 * instantly on re-entry while a fresh network copy loads in the background.
 */
@Singleton
class ConversationCacheDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    private fun keyFor(userId: String) = stringPreferencesKey("conversations_$userId")

    suspend fun read(userId: String): List<ConversationDto> {
        val raw = dataStore.data.first()[keyFor(userId)] ?: return emptyList()
        return try {
            json.decodeFromString<List<ConversationDto>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun write(userId: String, conversations: List<ConversationDto>) {
        dataStore.edit { prefs ->
            prefs[keyFor(userId)] = json.encodeToString(conversations)
        }
    }

    suspend fun clear(userId: String) {
        dataStore.edit { prefs -> prefs.remove(keyFor(userId)) }
    }
}
