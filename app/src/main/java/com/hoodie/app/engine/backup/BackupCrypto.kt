package com.hoodie.app.engine.backup

import java.io.*
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/** Header + ordered AEAD records + authenticated empty final record. No unauthenticated plaintext escapes. */
object BackupCrypto {
    private const val HEADER_SIZE = 8 + 4 + 4 + 16 + 8
    private fun cipher(mode: Int, key: javax.crypto.SecretKey, header: ByteArray, index: Int, size: Int): Cipher {
        val nonce = ByteBuffer.allocate(12).put(header, HEADER_SIZE - 8, 8).putInt(index).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(128, nonce))
            updateAAD(header)
            updateAAD(ByteBuffer.allocate(8).putInt(index).putInt(size).array())
        }
    }

    class Writer(output: OutputStream, password: CharArray) : Closeable {
        private val out = DataOutputStream(output)
        private val header = ByteBuffer.allocate(HEADER_SIZE).put(BackupFormat.magic).putInt(BackupFormat.VERSION)
            .putInt(BackupFormat.MIN_ITERATIONS).put(ByteArray(16).also { SecureRandom().nextBytes(it) })
            .put(ByteArray(8).also { SecureRandom().nextBytes(it) }).array()
        private val key = BackupKeyDerivation.derive(password, header.copyOfRange(16, 32), BackupFormat.MIN_ITERATIONS)
        private var index = 0
        private var total = HEADER_SIZE.toLong()
        private var finished = false
        init { out.write(header) }
        fun record(bytes: ByteArray) {
            requireBackup(!finished && bytes.isNotEmpty() && bytes.size <= BackupFormat.MAX_RECORD_BYTES)
            block(bytes)
        }
        private fun block(bytes: ByteArray) {
            requireBackup(index.toLong() <= BackupFormat.MAX_RECORDS)
            total += bytes.size + 24L
            requireBackup(total <= BackupFormat.MAX_FILE_BYTES)
            val encrypted = cipher(Cipher.ENCRYPT_MODE, key, header, index, bytes.size).doFinal(bytes)
            out.writeInt(index++); out.writeInt(bytes.size); out.write(encrypted)
        }
        fun finish() { requireBackup(!finished); block(ByteArray(0)); finished = true; out.flush() }
        override fun close() { out.close() }
    }

    fun read(input: InputStream, password: CharArray, consume: (ByteArray) -> Unit) {
        val stream = DataInputStream(input)
        try {
            val header = ByteArray(HEADER_SIZE).also { stream.readFully(it) }
            requireBackup(header.copyOfRange(0, 8).contentEquals(BackupFormat.magic), BackupError.INVALID_FILE)
            val params = ByteBuffer.wrap(header)
            requireBackup(params.getInt(8) == BackupFormat.VERSION, BackupError.INCOMPATIBLE)
            val key = BackupKeyDerivation.derive(password, header.copyOfRange(16, 32), params.getInt(12))
            var index = 0
            var total = HEADER_SIZE.toLong()
            while (true) {
                val sequence = stream.readInt()
                val size = stream.readInt()
                requireBackup(sequence == index && index.toLong() <= BackupFormat.MAX_RECORDS, BackupError.CORRUPTED)
                requireBackup(size in 0..BackupFormat.MAX_RECORD_BYTES, BackupError.INVALID_FILE)
                total += size + 24L
                requireBackup(total <= BackupFormat.MAX_FILE_BYTES, BackupError.INVALID_FILE)
                val encrypted = ByteArray(size + 16).also { stream.readFully(it) }
                val plain = try { cipher(Cipher.DECRYPT_MODE, key, header, index, size).doFinal(encrypted) }
                catch (_: AEADBadTagException) { throw BackupException(if (index == 0) BackupError.PASSWORD else BackupError.CORRUPTED) }
                try {
                    if (size == 0) {
                        requireBackup(index > 0 && stream.read() == -1, BackupError.CORRUPTED)
                        return
                    }
                    consume(plain)
                } finally { plain.fill(0) }
                index++
            }
        } catch (_: EOFException) { throw BackupException(BackupError.CORRUPTED) }
    }
}
