package md.sime.agenda.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import md.sime.agenda.model.UserType

data class SavedCredentials(
    val username: String,
    val password: String,
    val userType: UserType,
    val stayLoggedIn: Boolean
)

class CredentialStore(context: Context) {

    private val prefs: SharedPreferences = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            "sime_encrypted_creds",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }.getOrElse {
        context.applicationContext.getSharedPreferences("sime_creds_fallback", Context.MODE_PRIVATE)
    }

    fun saveCredentials(
        username: String,
        password: String,
        userType: UserType,
        stayLoggedIn: Boolean
    ) {
        prefs.edit()
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
            .putInt(KEY_USER_TYPE, userType.apexCode)
            .putBoolean(KEY_STAY_LOGGED_IN, stayLoggedIn)
            .apply()
    }

    fun getCredentials(): SavedCredentials? {
        val username = prefs.getString(KEY_USERNAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val password = prefs.getString(KEY_PASSWORD, null)?.takeIf { it.isNotEmpty() } ?: return null
        val userType = UserType.fromCode(prefs.getInt(KEY_USER_TYPE, UserType.ELEV.apexCode))
        val stayLoggedIn = prefs.getBoolean(KEY_STAY_LOGGED_IN, true)
        return SavedCredentials(
            username = username,
            password = password,
            userType = userType,
            stayLoggedIn = stayLoggedIn
        )
    }

    fun setStayLoggedIn(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_STAY_LOGGED_IN, enabled).apply()
    }

    fun clearPasswordKeepUsername() {
        prefs.edit()
            .remove(KEY_PASSWORD)
            .putBoolean(KEY_STAY_LOGGED_IN, false)
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_USER_TYPE = "user_type"
        private const val KEY_STAY_LOGGED_IN = "stay_logged_in"
    }
}
