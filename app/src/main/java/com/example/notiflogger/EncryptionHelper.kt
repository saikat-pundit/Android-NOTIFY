package com.example.notiflogger

import android.util.Base64
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object EncryptionHelper {
    // THE MASTER PASSWORD: Change this! 
    // It MUST be exactly the same in both this app and your "fetching" app.
    private const val SHARED_PASSWORD = "MySuperSecretEncryptionPassword123!"

    // This converts your password into a secure 256-bit AES Key using SHA-256 hashing
    private val secretKey: SecretKeySpec
        get() {
            val digest = MessageDigest.getInstance("SHA-256")
            val keyBytes = digest.digest(SHARED_PASSWORD.toByteArray(Charsets.UTF_8))
            return SecretKeySpec(keyBytes, "AES")
        }

    // A static Initialization Vector (IV) makes it much easier to decrypt across different apps
    private val iv = IvParameterSpec(ByteArray(16) { 0 })

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    fun decrypt(encryptedBase64: String): String {
        if (encryptedBase64.isEmpty()) return ""
        return try {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
            val decodedBytes = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            "" // Returns empty if the password is wrong or data isn't encrypted
        }
    }
}
