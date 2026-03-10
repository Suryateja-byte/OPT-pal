package com.sidekick.opt_pal.feature.vault

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

interface FileNameResolver {
    fun resolve(uri: Uri, contentResolver: ContentResolver): String
}

class ContentResolverFileNameResolver : FileNameResolver {
    override fun resolve(uri: Uri, contentResolver: ContentResolver): String {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: "document"
    }
}
