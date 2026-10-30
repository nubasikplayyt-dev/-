package app.investwatch.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Хранилище секретов: токен GitHub и ключ источника цен.
 *
 * Значение шифруется AES/GCM ключом, который лежит в Android Keystore и не
 * покидает устройство. Jetpack Security (EncryptedSharedPreferences) здесь
 * не используется: библиотека объявлена устаревшей, а задача решается
 * штатным Keystore без лишней зависимости.
 */
@Singleton
class SecureStore @Inject constructor(private val context: Context) {

    private val preferences by lazy {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    fun read(name: String): String? {
        val stored = preferences.getString(name, null) ?: return null
        return runCatching { decrypt(stored) }.getOrNull()
    }

    fun write(name: String, value: String?) {
        preferences.edit().apply {
            if (value.isNullOrBlank()) remove(name) else putString(name, encrypt(value))
        }.apply()
    }

    fun has(name: String): Boolean = !read(name).isNullOrBlank()

    /** Показываем токен владельцу только хвостом — целиком он не нужен. */
    fun masked(name: String): String? {
        val value = read(name) ?: return null
        return if (value.length <= 8) "••••" else "••••" + value.takeLast(4)
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        // Вектор инициализации кладём рядом с шифротекстом.
        return encode(cipher.iv) + SEPARATOR + encode(encrypted)
    }

    private fun decrypt(stored: String): String {
        val parts = stored.split(SEPARATOR)
        require(parts.size == 2) { "повреждённое значение" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, decode(parts[0])))
        return cipher.doFinal(decode(parts[1])).toString(Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)

    companion object {
        const val GITHUB_TOKEN = "github_token"
        const val PRICE_API_KEY = "price_api_key"

        private const val FILE = "secure_store"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "investwatch_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val TAG_BITS = 128
        private const val SEPARATOR = ":"
    }
}
