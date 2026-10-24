package dev.opencodegometer.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

@Suppress("DEPRECATION")
object SecureStore {
    private const val TAG = "SecureStore"
    private const val FILE = "secure_store"
    private const val KEY_API = "api_key"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun prefs(context: Context): SharedPreferences? {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            return try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val preferences = EncryptedSharedPreferences.create(
                    context.applicationContext,
                    FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                cached = preferences
                preferences
            } catch (e: Exception) {
                Log.e(TAG, "Encrypted storage unavailable", e)
                null
            }
        }
    }

    fun getApiKey(context: Context): String? =
        prefs(context)?.getString(KEY_API, null)?.takeIf { it.isNotBlank() }

    @SuppressLint("ApplySharedPref")
    fun setApiKey(context: Context, key: String): Boolean {
        val preferences = prefs(context) ?: return false
        return try {
            preferences.edit().putString(KEY_API, key).commit()
        } catch (e: Exception) {
            Log.e(TAG, "Could not save API key", e)
            false
        }
    }

    @SuppressLint("ApplySharedPref")
    fun clear(context: Context) {
        val preferences = prefs(context) ?: return
        runCatching { preferences.edit().remove(KEY_API).commit() }
    }
}
