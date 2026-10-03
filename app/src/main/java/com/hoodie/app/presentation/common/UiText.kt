package com.hoodie.app.presentation.common

import android.content.Context
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes

/** A localized presentation message whose parameters remain testable without Android resources. */
sealed interface UiText {
    data class Resource(@StringRes val resource: Int, val arguments: List<Any> = emptyList()) : UiText
    data class Quantity(@PluralsRes val resource: Int, val quantity: Int, val arguments: List<Any> = emptyList()) : UiText
}

fun UiText.resolve(context: Context): String {
    fun List<Any>.resolved() = map { if (it is UiText) it.resolve(context) else it }.toTypedArray()
    return when (this) {
        is UiText.Resource -> context.getString(resource, *arguments.resolved())
        is UiText.Quantity -> context.resources.getQuantityString(resource, quantity, *arguments.resolved())
    }
}
