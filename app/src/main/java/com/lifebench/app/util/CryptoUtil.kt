package com.lifebench.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 密码保险箱加密：基于 Android Keystore 的 AES/GCM 密钥，密钥不出安全硬件。
 * 明文在内存中即加密为 Base64 密文入库；解密仅在本地完成。
 */
object CryptoUtil {
    private const val KEY_ALIAS = "lifebench_aes_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_LEN = 12

    private fun getKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(KEY_ALIAS)) return ks.getKey(KEY_ALIAS, null) as SecretKey
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val enc = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + enc.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(enc, 0, combined, iv.size, enc.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(base64: String): String {
        val combined = Base64.decode(base64, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, IV_LEN)
        val enc = combined.copyOfRange(IV_LEN, combined.size)
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(enc), Charsets.UTF_8)
    }
}
