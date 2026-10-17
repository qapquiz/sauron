package dev.fireforge.sauron.ingest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/** The one image-read permission helper. The worker and the UI both ask it. */
object ImagePermission {

    fun name(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun granted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, name(Build.VERSION.SDK_INT)) ==
            PackageManager.PERMISSION_GRANTED
    }
