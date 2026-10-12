package com.uit.finance.core.datastore.secure

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Values are encrypted with a non-exportable AES-256/GCM key that lives in the Android Keystore
 * (hardware-backed where available) and stored as `base64(iv || ciphertext)` in a private
 * SharedPreferences file. Replaces the deprecated `androidx.security.crypto`.
 */
internal class AndroidKeystoreSecureStorage(
    context: Context,
    prefsName: String = PREFS_NAME,
    private val keyAlias: String = KEY_ALIAS,
) : SecureStorage {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val key: SecretKey by lazy { loadOrCreateKey() }

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, encrypt(value)).apply()
    }

    override fun getString(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            decrypt(stored)
        } catch (e: GeneralSecurityException) {
            if (!e.isPermanent()) throw SecureStorageUnavailableException(e)
            // Khoá Keystore đã bị huỷ hoặc thay (đặt lại khoá màn hình, restore máy): giá trị không cứu được.
            prefs.edit().remove(key).apply()
            null
        } catch (e: IllegalArgumentException) {
            // Base64 hỏng / quá ngắn: dữ liệu rác, không bao giờ giải mã được.
            prefs.edit().remove(key).apply()
            null
        } catch (e: RuntimeException) {
            // ProviderException, KeyStoreException bọc trong RuntimeException...: Keystore tạm bận. GIỮ giá trị,
            // nếu không một lần lỗi thoáng qua sẽ làm mất khoá DB và kéo theo cả các thay đổi chưa gửi.
            throw SecureStorageUnavailableException(e)
        }
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.encodeToByteArray())
        return Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > IV_LENGTH_BYTES) { "ciphertext too short" }
        val iv = bytes.copyOfRange(0, IV_LENGTH_BYTES)
        val cipherText = bytes.copyOfRange(IV_LENGTH_BYTES, bytes.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText).decodeToString()
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let { return it }

        val spec = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setRandomizedEncryptionRequired(true)
            .build()

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    /**
     * Lỗi chứng tỏ giá trị không bao giờ giải mã được nữa: tag GCM sai (khoá đã đổi), khoá bị vô hiệu hoá
     * vĩnh viễn hoặc không còn lấy ra được. Lỗi khác của Keystore coi là tạm thời.
     */
    private fun GeneralSecurityException.isPermanent(): Boolean =
        this is AEADBadTagException || this is KeyPermanentlyInvalidatedException || this is UnrecoverableKeyException

    private companion object {
        const val PREFS_NAME = "finance_secure_storage"
        const val KEY_ALIAS = "finance_secure_storage_aes_gcm"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH_BYTES = 12
        const val TAG_LENGTH_BITS = 128
    }
}
