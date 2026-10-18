package com.hoodie.app.engine.backup

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupKeyDerivation {
    fun derive(password: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        requireBackup(salt.size == 16 && iterations in BackupFormat.MIN_ITERATIONS..BackupFormat.MAX_ITERATIONS, BackupError.INVALID_FILE)
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try { SecretKeySpec(bytes, "AES") } finally { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    fun isStrong(password: CharArray): Boolean = password.size in 12..256 && password.toSet().size >= 5
}
