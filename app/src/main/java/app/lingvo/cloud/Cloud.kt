package app.lingvo.cloud

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import java.security.SecureRandom
import java.util.UUID

/** Кто вошёл в приложение. Вход не обязателен: без него всё работает локально. */
data class Account(val uid: String, val name: String, val email: String, val anonymous: Boolean)

/**
 * Облако Firebase: вход через Google и обмен прогрессом по коду.
 *
 * Если файла `app/google-services.json` нет, облачных функций просто нет ([isConfigured] = false),
 * а приложение работает как раньше — локально.
 */
class Cloud(private val context: Context) {
    private val firebase: FirebaseApp? = runCatching { FirebaseApp.initializeApp(context) }
        .onFailure { Log.w(TAG, "Firebase не настроен: ${it.message}") }.getOrNull()

    val isConfigured: Boolean get() = firebase != null

    private val auth: FirebaseAuth? = firebase?.let { runCatching { FirebaseAuth.getInstance(it) }.getOrNull() }
    val database: FirebaseDatabase? = firebase?.let { runCatching { FirebaseDatabase.getInstance(it) }.getOrNull() }

    private val _account = MutableStateFlow(auth?.currentUser?.toAccount())
    val account: StateFlow<Account?> = _account

    /** ID веб-клиента OAuth; появляется вместе с google-services.json. */
    private val webClientId: String? = runCatching {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (id == 0) null else context.getString(id)
    }.getOrNull()

    private fun com.google.firebase.auth.FirebaseUser.toAccount() = Account(
        uid = uid,
        name = displayName?.takeIf { it.isNotBlank() } ?: "Без имени",
        email = email.orEmpty(),
        anonymous = isAnonymous,
    )

    private fun refresh() { _account.value = auth?.currentUser?.toAccount() }

    /**
     * Вход через аккаунт Google (системное окно выбора аккаунта).
     * Возвращает null при успехе или текст ошибки для показа пользователю.
     */
    suspend fun signInWithGoogle(activityContext: Context): String? {
        val a = auth ?: return NOT_CONFIGURED
        val clientId = webClientId ?: return NOT_CONFIGURED
        return try {
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
            val option = GetGoogleIdOption.Builder()
                .setServerClientId(clientId)
                // false — показать все аккаунты на устройстве, а не только те, что уже входили.
                .setFilterByAuthorizedAccounts(false)
                .setAutoSelectEnabled(false)
                .setNonce(nonce)
                .build()
            val response = CredentialManager.create(context)
                .getCredential(activityContext, GetCredentialRequest.Builder().addCredentialOption(option).build())
            val credential = response.credential
            if (credential !is CustomCredential || credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                return "Не удалось получить аккаунт Google"
            }
            val token = GoogleIdTokenCredential.createFrom(credential.data).idToken
            a.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            refresh()
            null
        } catch (e: Exception) {
            Log.w(TAG, "Вход не удался", e)
            when {
                e.javaClass.simpleName.contains("NoCredential") ->
                    "На устройстве нет аккаунта Google или вход отменён"
                else -> e.localizedMessage ?: "Не удалось войти"
            }
        }
    }

    /** Вход без аккаунта Google — нужен, чтобы читать и писать обмен прогрессом. */
    suspend fun signInAnonymously(): String? {
        val a = auth ?: return NOT_CONFIGURED
        if (a.currentUser != null) { refresh(); return null }
        return try {
            a.signInAnonymously().await()
            refresh()
            null
        } catch (e: Exception) {
            Log.w(TAG, "Анонимный вход не удался", e)
            e.localizedMessage ?: "Нет связи с сервером"
        }
    }

    suspend fun signOut() {
        auth?.signOut()
        runCatching {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        }
        refresh()
    }

    companion object {
        private const val TAG = "Cloud"
        const val NOT_CONFIGURED = "Облачные функции не настроены в этой сборке"

        /** Случайный идентификатор устройства — на случай, если понадобится новый код. */
        fun newDeviceId(): String = UUID.randomUUID().toString()
    }
}
