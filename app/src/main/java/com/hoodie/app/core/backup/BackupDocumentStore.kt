package com.hoodie.app.core.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.hoodie.app.engine.backup.BackupError
import com.hoodie.app.engine.backup.BackupException
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BackupDocumentStore @Inject constructor(@ApplicationContext private val context: Context) {
    fun read(uri: Uri) = context.contentResolver.openInputStream(uri) ?: throw BackupException(BackupError.STORAGE)
    fun write(uri: Uri): java.io.OutputStream {
        // SAF destinations must be content URIs. Never let an activity result overwrite a private file:// path.
        if (uri.scheme != "content") throw BackupException(BackupError.STORAGE)
        return context.contentResolver.openOutputStream(uri, "wt") ?: throw BackupException(BackupError.STORAGE)
    }
    fun delete(uri: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }.getOrDefault(false)
}
