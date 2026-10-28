package com.dosprint.ui

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Opening Setup crashed because a paper grid built on `LazyColumn` was placed inside Setup's own
 * `LazyColumn`. A scrollable inside a scrollable, with no height bound, throws "infinite maximum
 * height constraints" as soon as it is measured — and it did, on every launch of that screen.
 *
 * Compose offers no compile-time check for that, so this scans the components that are rendered
 * *inside* a scrolling screen and fails if one of them reaches for a lazy list itself.
 */
class NoNestedScrollingTest {

    private val insideScrollingScreens = listOf(
        "src/main/kotlin/com/dosprint/ui/components/LayoutPickers.kt",
        "src/main/kotlin/com/dosprint/ui/components/UniformControls.kt",
        "src/main/kotlin/com/dosprint/ui/components/SettingsComponents.kt",
        "src/main/kotlin/com/dosprint/ui/components/LivePreviewPanel.kt",
    )

    private val lazyContainers = listOf("LazyColumn(", "LazyRow(", "LazyVerticalGrid(", "LazyHorizontalGrid(")

    @Test
    fun `components placed inside scrolling screens are never lazy lists themselves`() {
        insideScrollingScreens.forEach { path ->
            val file = File(path)
            if (!file.exists()) return@forEach
            val code = file.readLines()
                .filterNot { it.trimStart().startsWith("*") || it.trimStart().startsWith("//") }
                .joinToString("\n")
            lazyContainers.forEach { container ->
                assertFalse(
                    "$path uses $container but is rendered inside a scrolling screen",
                    code.contains(container),
                )
            }
        }
    }
}
