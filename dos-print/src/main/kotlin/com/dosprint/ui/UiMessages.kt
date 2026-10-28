package com.dosprint.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dosprint.R

object UiMessages {

    private val setup: Map<String, Int> = mapOf(
        "printer_selected" to R.string.message_printer_selected,
        "job_submitted" to R.string.message_job_submitted,
        "no_printer" to R.string.message_no_printer,
        "no_document" to R.string.message_no_document,
        "submit_failed" to R.string.message_submit_failed,
        "layout_preset_applied" to R.string.message_layout_applied,
    )

    @StringRes
    fun setupRes(key: String): Int? = setup[key]

    fun setupText(context: Context, key: String): String? =
        setup[key]?.let { context.getString(it) }
}

@Composable
fun setupMessage(key: String?): String? {
    val context = LocalContext.current
    return key?.let { UiMessages.setupText(context, it) ?: it }
}
