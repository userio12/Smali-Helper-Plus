package com.riyadm.smalihelperplus.data.ai

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Storage for the user's AI API key. Only ciphertext ever touches disk. */
interface ApiKeyStore {
    /** True while a (decryptable) key is stored. */
    val hasKey: StateFlow<Boolean>
    fun load(): String?
    fun save(key: String)
    fun clear()
}

/** AES-256/GCM helper with a pluggable key, so the cryptography can be unit-tested without the Android Keystore. */
class AesGcmBox(private val keyProvider: () -> SecretKey) {
    class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

    fun encrypt(plain: ByteArray): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keyProvider())
        // The Android Keystore generates the IV itself (randomized encryption is required); software keys do too.
        return Sealed(cipher.iv, cipher.doFinal(plain))
    }

    fun decrypt(sealed: Sealed): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyProvider(), GCMParameterSpec(TAG_BITS, sealed.iv))
        return cipher.doFinal(sealed.ciphertext)
    }

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128

        /** Software key for tests. */
        fun randomSoftwareKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }.generateKey()
    }
}

/**
 * Keeps the key in a SharedPreferences file named [FILE] (excluded from backups and device transfer in
 * res/xml/backup_rules.xml and data_extraction_rules.xml) encrypted with a non-exportable AES-256/GCM key held by the
 * Android Keystore under alias [KEY_ALIAS]. If the Keystore key is lost (e.g. after a restore on another device) the
 * stored value is discarded and the user simply enters the key again.
 */
@Singleton
class KeystoreApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) : ApiKeyStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val box = AesGcmBox(::getOrCreateKey)
    private val _hasKey = MutableStateFlow(prefs.contains(KEY_CIPHERTEXT) && prefs.contains(KEY_IV))
    override val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    @Synchronized
    override fun load(): String? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val ct = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        return try {
            val plain = box.decrypt(AesGcmBox.Sealed(Base64.getDecoder().decode(iv), Base64.getDecoder().decode(ct)))
            String(plain, Charsets.UTF_8)
        } catch (e: Exception) {
            // Key invalidated, ciphertext corrupted or restored on another device: forget it.
            clear()
            null
        }
    }

    @Synchronized
    override fun save(key: String) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            clear()
            return
        }
        val sealed = box.encrypt(trimmed.toByteArray(Charsets.UTF_8))
        prefs.edit {
            putString(KEY_IV, Base64.getEncoder().encodeToString(sealed.iv))
            putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(sealed.ciphertext))
        }
        _hasKey.value = true
    }

    @Synchronized
    override fun clear() {
        prefs.edit {
            remove(KEY_IV)
            remove(KEY_CIPHERTEXT)
        }
        _hasKey.value = false
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val FILE = "secure_ai_prefs"
        const val KEY_ALIAS = "smali_ai_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_IV = "iv"
        private const val KEY_CIPHERTEXT = "ct"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {
    @Binds abstract fun bindApiKeyStore(impl: KeystoreApiKeyStore): ApiKeyStore
}
