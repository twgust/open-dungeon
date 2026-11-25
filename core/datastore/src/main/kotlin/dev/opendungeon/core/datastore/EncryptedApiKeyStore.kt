package dev.opendungeon.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.opendungeon.core.data.ApiKeyStore
import dev.opendungeon.core.model.ApiKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Keeps the OpenRouter key in a dedicated Preferences DataStore, encrypted with a Keystore key.
 * The key never leaves the device: it is deliberately not synced to Firebase.
 *
 * Singleton because DataStore allows only one instance per file.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
public class EncryptedApiKeyStore(context: Context) : ApiKeyStore {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create {
        context.preferencesDataStoreFile(FILE_NAME)
    }
    private val cipher = KeystoreCipher(KEYSTORE_ALIAS)

    override val apiKey: Flow<ApiKey?> = dataStore.data
        .map { prefs -> prefs[ENCRYPTED_KEY]?.let(cipher::decryptOrNull)?.let(ApiKey::parse) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override suspend fun write(apiKey: ApiKey) {
        val encrypted = withContext(Dispatchers.IO) { cipher.encrypt(apiKey.value) }
        dataStore.edit { it[ENCRYPTED_KEY] = encrypted }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(ENCRYPTED_KEY) }
    }

    private companion object {
        const val FILE_NAME = "openrouter_credentials"
        const val KEYSTORE_ALIAS = "opendungeon.openrouter.api_key"
        val ENCRYPTED_KEY = stringPreferencesKey("api_key")
    }
}
