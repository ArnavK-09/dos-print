package com.dosprint.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dosprint.R
import com.dosprint.core.ErrorReporter
import com.dosprint.domain.model.ColorMode
import com.dosprint.domain.model.PrintResolution
import com.dosprint.domain.model.ThemeOption
import com.dosprint.ui.SettingsAction
import com.dosprint.ui.SettingsUiState
import com.dosprint.ui.colorModeLabel
import com.dosprint.ui.common.DosPrintCard
import com.dosprint.ui.common.DosPrintSection
import com.dosprint.ui.common.SectionHeader
import com.dosprint.ui.components.CountStepper
import com.dosprint.ui.components.LabeledSwitch
import com.dosprint.ui.components.OptionChips
import com.dosprint.ui.components.SettingRow
import com.dosprint.ui.components.UniformButton
import com.dosprint.ui.components.UniformButtonRow
import com.dosprint.ui.theme.DosPrintDimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenLogs: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    SettingsBody(
        state = state,
        onAction = onAction,
        onOpenLogs = onOpenLogs,
        contentPadding = contentPadding,
        modifier = modifier,
    )
}

@Composable
private fun SettingsBody(
    state: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
    onOpenLogs: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
    ) {
        item(key = "appearance") { AppearanceCard(state, onAction) }
        item(key = "defaults") { DefaultsCard(state, onAction) }
        item(key = "advanced") { AdvancedCard(state, onAction) }
        item(key = "diagnostics") { DiagnosticsCard(onOpenLogs) }
        item(key = "about") { AboutCard(state, onAction) }
    }
}

@Composable
private fun AppearanceCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    DosPrintCard {
        Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap)) {
            SectionHeader(title = stringResource(R.string.settings_appearance))
            OptionChips(
                label = stringResource(R.string.settings_theme),
                options = ThemeOption.entries,
                selected = state.preferences.theme,
                optionLabel = { themeLabel(it) },
                onSelect = { onAction(SettingsAction.SelectTheme(it)) },
            )
            LabeledSwitch(
                label = stringResource(R.string.settings_dynamic_color),
                summary = stringResource(R.string.settings_dynamic_color_summary),
                checked = state.preferences.useDynamicColor,
                onCheckedChange = { onAction(SettingsAction.ToggleDynamicColor(it)) },
            )
            LabeledSwitch(
                label = stringResource(R.string.settings_contrast),
                checked = state.preferences.increaseContrast,
                onCheckedChange = { onAction(SettingsAction.ToggleContrast(it)) },
            )
            LabeledSwitch(
                label = stringResource(R.string.settings_liquid_glass),
                summary = stringResource(R.string.settings_liquid_glass_summary),
                checked = state.preferences.liquidGlassEnabled,
                onCheckedChange = { onAction(SettingsAction.ToggleLiquidGlass(it)) },
            )
        }
    }
}

@Composable
private fun DefaultsCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    val context = LocalContext.current
    DosPrintCard {
        Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap)) {
            SectionHeader(title = stringResource(R.string.settings_defaults))
            OptionChips(
                label = stringResource(R.string.settings_default_quality),
                options = PrintResolution.entries,
                selected = state.preferences.defaultQuality,
                optionLabel = { it.label },
                onSelect = { onAction(SettingsAction.SelectDefaultQuality(it)) },
            )
            OptionChips(
                label = stringResource(R.string.settings_default_color),
                options = ColorMode.entries,
                selected = state.preferences.defaultColorMode,
                optionLabel = { it.colorModeLabel(context) },
                onSelect = { onAction(SettingsAction.SelectDefaultColorMode(it)) },
            )
            SettingRow(label = stringResource(R.string.settings_default_copies)) {
                CountStepper(
                    value = state.preferences.defaultCopies,
                    onValueChange = { onAction(SettingsAction.SetDefaultCopies(it)) },
                    label = COPIES_LABEL,
                )
            }
        }
    }
}

@Composable
private fun AdvancedCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    DosPrintCard {
        Column(verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap)) {
            SectionHeader(title = stringResource(R.string.settings_advanced))
            LabeledSwitch(
                label = stringResource(R.string.settings_low_memory),
                summary = stringResource(R.string.settings_low_memory_summary),
                checked = state.preferences.lowMemoryMode,
                onCheckedChange = { onAction(SettingsAction.ToggleLowMemory(it)) },
            )
            LabeledSwitch(
                label = stringResource(R.string.settings_auto_discover),
                checked = state.preferences.autoDiscoverPrinters,
                onCheckedChange = { onAction(SettingsAction.ToggleAutoDiscover(it)) },
            )
        }
    }
}

@Composable
private fun AboutCard(state: SettingsUiState, onAction: (SettingsAction) -> Unit) {
    DosPrintCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DosPrintDimens.cardGap),
        ) {
            SectionHeader(title = stringResource(R.string.settings_about))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.settings_version, state.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onAction(SettingsAction.ClearPreviewCache) },
                modifier = Modifier.semantics { contentDescription = CLEAR_CACHE_LABEL },
            ) {
                Text(stringResource(R.string.settings_clear_cache))
            }
        }
    }
}

@Composable
private fun themeLabel(option: ThemeOption): String = stringResource(
    when (option) {
        ThemeOption.SYSTEM -> R.string.settings_theme_system
        ThemeOption.LIGHT -> R.string.settings_theme_light
        ThemeOption.DARK -> R.string.settings_theme_dark
    },
)

private const val COPIES_LABEL = "Copies"
private const val CLEAR_CACHE_LABEL = "Clear preview cache"

/** The way in to the in-app log viewer. */
@Composable
private fun DiagnosticsCard(onOpenLogs: () -> Unit) {
    var errorCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        errorCount = withContext(Dispatchers.IO) { ErrorReporter.count() }
    }
    DosPrintSection(
        title = stringResource(R.string.settings_diagnostics),
        subtitle = stringResource(R.string.settings_diagnostics_subtitle),
        icon = Icons.Filled.Warning,
    ) {
        UniformButtonRow(
            buttons = listOf(
                UniformButton(
                    label = stringResource(R.string.logs_open, errorCount),
                    icon = Icons.Filled.Warning,
                    emphasized = true,
                    onClick = onOpenLogs,
                ),
            ),
        )
    }
}
