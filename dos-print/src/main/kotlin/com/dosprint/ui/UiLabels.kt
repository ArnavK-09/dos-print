package com.dosprint.ui

import android.content.Context
import com.dosprint.R
import com.dosprint.domain.model.ColorMode
import com.dosprint.domain.model.DuplexMode
import com.dosprint.domain.model.MarginPreset
import com.dosprint.domain.model.PageRotation
import com.dosprint.domain.model.PrintJobStatus
import com.dosprint.domain.model.PrinterAvailability
import com.dosprint.domain.model.ScalingMode
import com.dosprint.domain.model.WatermarkStyle

fun PrintJobStatus.jobStatusLabel(context: Context): String = context.getString(
    when (this) {
        PrintJobStatus.QUEUED -> R.string.status_queued
        PrintJobStatus.PREPARING -> R.string.status_preparing
        PrintJobStatus.BLOCKED -> R.string.status_blocked
        PrintJobStatus.PRINTING -> R.string.status_printing
        PrintJobStatus.COMPLETED -> R.string.status_completed
        PrintJobStatus.CANCELLED -> R.string.status_cancelled
        PrintJobStatus.FAILED -> R.string.status_failed
    },
)

fun PrinterAvailability.printerAvailabilityLabel(context: Context): String = context.getString(
    when (this) {
        PrinterAvailability.ONLINE -> R.string.printers_status_online
        PrinterAvailability.BUSY -> R.string.printers_status_busy
        PrinterAvailability.OFFLINE -> R.string.printers_status_offline
        PrinterAvailability.NO_PAPER -> R.string.printers_status_no_paper
        PrinterAvailability.ERROR -> R.string.printers_status_error
        PrinterAvailability.UNKNOWN -> R.string.printers_status_unknown
    },
)

fun ColorMode.colorModeLabel(context: Context): String = context.getString(
    when (this) {
        ColorMode.COLOR -> R.string.color_color
        ColorMode.GRAYSCALE -> R.string.color_grayscale
        ColorMode.MONOCHROME -> R.string.color_mono
    },
)

fun DuplexMode.duplexLabel(context: Context): String = context.getString(
    when (this) {
        DuplexMode.NONE -> R.string.duplex_none
        DuplexMode.LONG_EDGE -> R.string.duplex_long_edge
        DuplexMode.SHORT_EDGE -> R.string.duplex_short_edge
    },
)

fun ScalingMode.scalingLabel(context: Context): String = context.getString(
    when (this) {
        ScalingMode.FIT_TO_PAGE -> R.string.scaling_fit
        ScalingMode.FILL_PAGE -> R.string.scaling_fill
        ScalingMode.ACTUAL_SIZE -> R.string.scaling_actual
    },
)

fun MarginPreset.marginLabel(context: Context): String = context.getString(
    when (this) {
        MarginPreset.PRINTER_DEFAULT -> R.string.margins_default
        MarginPreset.MINIMUM -> R.string.margins_minimum
        MarginPreset.NONE -> R.string.margins_none
    },
)

fun PageRotation.rotationLabel(context: Context): String = context.getString(
    when (this) {
        PageRotation.NONE -> R.string.rotation_none
        PageRotation.CLOCKWISE_90 -> R.string.rotation_90
        PageRotation.UPSIDE_DOWN -> R.string.rotation_180
        PageRotation.COUNTER_CLOCKWISE_90 -> R.string.rotation_270
    },
)

fun WatermarkStyle.watermarkLabel(context: Context): String = context.getString(
    when (this) {
        WatermarkStyle.NONE -> R.string.setup_watermark_none
        WatermarkStyle.TEXT -> R.string.setup_watermark_text
    },
)
