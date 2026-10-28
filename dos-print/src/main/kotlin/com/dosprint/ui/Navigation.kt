package com.dosprint.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.dosprint.R
import com.dosprint.ui.components.DosPrintIcons
import com.dosprint.ui.components.liquidglass.LiquidBottomTab
import com.dosprint.ui.components.liquidglass.LiquidBottomTabs
import com.dosprint.ui.theme.DosPrintDimens
import com.kyant.backdrop.Backdrop

enum class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    HOME(
        route = Route.Home,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    DOCUMENTS(
        route = Route.Documents,
        labelRes = R.string.nav_documents,
        selectedIcon = DosPrintIcons.Document,
        unselectedIcon = DosPrintIcons.Document,
    ),
    PRINTERS(
        route = Route.Printers,
        labelRes = R.string.nav_printers,
        selectedIcon = DosPrintIcons.Printer,
        unselectedIcon = DosPrintIcons.Printer,
    ),
    SETTINGS(
        route = Route.Settings,
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    ),
}

object Route {
    const val Home = "home"
    const val Documents = "documents"
    const val Printers = "printers"
    const val Setup = "setup"
    const val Preview = "preview"
    const val Settings = "settings"
    const val Logs = "logs"
}

/**
 * Settings is a phone destination too. It was previously listed only for wide windows, which left
 * Settings — and the diagnostics inside it — unreachable on a phone.
 */
private val PhoneDestinations = listOf(
    TopLevelDestination.HOME,
    TopLevelDestination.PRINTERS,
    TopLevelDestination.SETTINGS,
)

private val ExpandedDestinations = listOf(
    TopLevelDestination.HOME,
    TopLevelDestination.DOCUMENTS,
    TopLevelDestination.PRINTERS,
    TopLevelDestination.SETTINGS,
)

@Composable
fun DosPrintBottomBar(
    currentRoute: String?,
    windowSizeClass: WindowSizeClass,
    onNavigate: (TopLevelDestination) -> Unit,
    backdrop: Backdrop,
    liquidGlassEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val destinations = if (windowSizeClass.isExpandedWidth()) {
        ExpandedDestinations
    } else {
        PhoneDestinations
    }
    if (!liquidGlassEnabled) {
        NavigationBar(modifier = modifier) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                val label = stringResource(destination.labelRes)
                NavigationBarItem(
                    selected = selected,
                    onClick = { onNavigate(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(label) },
                    modifier = Modifier.semantics { contentDescription = label },
                )
            }
        }
        return
    }

    var selectedIndex by remember(destinations) {
        mutableIntStateOf(destinations.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0))
    }
    LaunchedEffect(currentRoute, destinations) {
        val routeIndex = destinations.indexOfFirst { it.route == currentRoute }
        if (routeIndex >= 0) selectedIndex = routeIndex
    }
    val colors = MaterialTheme.colorScheme
    LiquidBottomTabs(
        selectedTabIndex = { selectedIndex },
        onTabSelected = { index ->
            destinations.getOrNull(index)?.let { destination ->
                selectedIndex = index
                onNavigate(destination)
            }
        },
        backdrop = backdrop,
        tabsCount = destinations.size,
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
    ) {
        destinations.forEachIndexed { index, destination ->
            val label = stringResource(destination.labelRes)
            val selected = selectedIndex == index
            LiquidBottomTab(
                onClick = {
                    selectedIndex = index
                    onNavigate(destination)
                },
                modifier = Modifier.semantics { contentDescription = label },
            ) {
                Icon(
                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                    contentDescription = null,
                    tint = if (selected) colors.onSurface else colors.onSurfaceVariant,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun DosPrintNavigationRail(
    currentRoute: String?,
    windowSizeClass: WindowSizeClass,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!windowSizeClass.isExpandedWidth()) {
        return
    }
    NavigationRail(modifier = modifier) {
        ExpandedDestinations.forEach { destination ->
            val selected = currentRoute == destination.route
            val label = stringResource(destination.labelRes)
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(label) },
                modifier = Modifier.semantics { contentDescription = label },
            )
        }
    }
}

@Composable
fun WindowSizeClass.isExpandedWidth(): Boolean = widthSizeClass == WindowWidthSizeClass.Expanded

@Composable
fun WindowSizeClass.screenPadding(): PaddingValues {
    val horizontal = if (isExpandedWidth()) WideScreenHorizontal else CompactScreenHorizontal
    return PaddingValues(start = horizontal, end = horizontal, top = ScreenTop, bottom = ScreenBottom)
}

private val CompactScreenHorizontal = DosPrintDimens.screenHorizontal
private val WideScreenHorizontal = DosPrintDimens.space24
private val ScreenTop = DosPrintDimens.screenTop
private val ScreenBottom = DosPrintDimens.screenBottom

/**
 * Whether an import should take over the screen.
 *
 * Only the two entry points qualify. An import cannot be triggered from anywhere else, so this is a
 * guard against navigating away from a screen the user is in the middle of rather than a real
 * restriction.
 *
 * Kept as a function rather than an inline check because it was previously written against a route
 * captured once at composition time, which was `null` on the first frame and so never matched. That
 * made picking a file do nothing at all, with no error, because nothing failed — the condition was
 * simply always false.
 */
fun shouldOpenAfterImport(currentRoute: String?): Boolean =
    currentRoute == Route.Home || currentRoute == Route.Documents
