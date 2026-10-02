package com.hoodie.app.core.database

/** Carrega a implementação JNI antes de Room ou do bootstrap usarem SQLCipher. */
object SqlCipherNativeLoader {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            System.loadLibrary("sqlcipher")
            loaded = true
        }
    }
}
