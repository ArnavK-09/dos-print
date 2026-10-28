package com.dosprint.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dosprint.ui.theme.DosPrintDimens

/**
 * The single source of truth for screen padding.
 *
 * Two separate traps make this easy to get wrong, and both have been hit in this app:
 *
 * 1. Material's `Scaffold` default `contentWindowInsets` is the **system bars** only, which in
 *    portrait are entirely vertical. Handing its inner padding straight to a screen leaves zero
 *    left and right margin and every row sits flush against the display edge.
 * 2. Discarding its inner padding in favour of window insets is equally wrong, because
 *    `innerPadding.bottom` is the height of the bottom bar, not just the navigation bar inset.
 *    Content then scrolls underneath an 80 dp navigation bar and the last item is unreachable.
 *
 * So the shell zeroes Scaffold's insets, passes the resulting inner padding here as [content], and
 * this adds the design margin on top of it. Cutout insets still come from the window, because
 * Scaffold's zeroed insets no longer report them.
 */
@Composable
fun rememberScreenPadding(
    content: PaddingValues = PaddingValues(0.dp),
    horizontal: Dp = DosPrintDimens.screenHorizontal,
    top: Dp = DosPrintDimens.screenTop,
    bottom: Dp = DosPrintDimens.screenBottom,
): PaddingValues {
    val direction = LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    val bars = WindowInsets.systemBars.asPaddingValues()
    return PaddingValues(
        start = maxOf(
            content.calculateStartPadding(direction),
            safe.calculateStartPadding(direction),
        ) + horizontal,
        end = maxOf(
            content.calculateEndPadding(direction),
            safe.calculateEndPadding(direction),
        ) + horizontal,
        top = maxOf(
            content.calculateTopPadding(),
            bars.calculateTopPadding(),
        ) + top,
        bottom = maxOf(
            content.calculateBottomPadding(),
            bars.calculateBottomPadding(),
        ) + bottom,
    )
}

@Composable
fun screenGutter(wide: Boolean): Dp =
    if (wide) DosPrintDimens.space32 else DosPrintDimens.screenHorizontal
