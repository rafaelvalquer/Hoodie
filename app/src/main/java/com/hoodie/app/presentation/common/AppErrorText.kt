package com.hoodie.app.presentation.common

import android.content.Context
import com.hoodie.app.R
import com.hoodie.app.core.error.*

/** Localization belongs to presentation, including transient snackbar messages. */
fun Context.appErrorText(error: AppError): String = getString(when (error) {
    DatabaseError.Unavailable -> R.string.error_database_unavailable
    DatabaseError.ReadFailed -> R.string.error_database_read
    DatabaseError.WriteFailed -> R.string.error_database_write
    LocationError.Unavailable -> R.string.error_location_unavailable
    LocationError.PermissionDenied -> R.string.error_location_permission
    LocationError.Disabled -> R.string.error_location_disabled
    is PlaceError.NotFound -> R.string.error_place_missing
    PlaceError.SaveFailed -> R.string.error_place_save
    UsageAccessError.PermissionDenied -> R.string.error_usage_permission
    UsageAccessError.Unavailable -> R.string.error_usage_unavailable
    UsageAccessError.ReadFailed -> R.string.error_usage_read
})
