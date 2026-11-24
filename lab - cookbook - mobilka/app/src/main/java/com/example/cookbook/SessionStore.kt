package com.example.cookbook

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class SessionStore(context: Context, name: String = "cookbook-session", origin: String? = null) {
    private val alias = "$name-aes"
    private val file = AtomicFile(File(context.noBackupFilesDir, name))
    private val originBytes = origin?.toByteArray(Charsets.UTF_8)

    fun load(): String? = synchronized(fileLock) {
        val payload = try {
            file.readFully()
        } catch (_: FileNotFoundException) {
            return@synchronized null
        }

        try {
            if (payload.size <= IV_SIZE + TAG_SIZE_BYTES) throw GeneralSecurityException()
            val key = keyStore().getKey(alias, null) as? SecretKey ?: throw InvalidKeyException()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_SIZE_BYTES * 8, payload, 0, IV_SIZE))
            originBytes?.let(cipher::updateAAD)
            cipher.doFinal(payload, IV_SIZE, payload.size - IV_SIZE)
                .toString(Charsets.UTF_8)
                .takeIf { it.isNotBlank() }
        } catch (_: GeneralSecurityException) {
            clear()
            null
        }
    }

    fun save(token: String) = synchronized(fileLock) {
        val key = keyStore().getKey(alias, null) as? SecretKey ?: generateKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        originBytes?.let(cipher::updateAAD)
        check(cipher.iv.size == IV_SIZE)
        val payload = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))

        val stream = file.startWrite()
        try {
            stream.write(payload)
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            throw e
        }
        if (!file.readFully().contentEquals(payload)) throw IOException("Session write failed")
    }

    fun clear() = synchronized(fileLock) {
        file.delete()
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private fun generateKey(): SecretKey = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_SIZE_BYTES = 16
        val fileLock = Any()
    }
}
