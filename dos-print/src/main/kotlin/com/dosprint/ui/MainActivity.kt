package com.dosprint.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dosprint.DosPrintApplication
import com.dosprint.R
import com.dosprint.core.AppLog
import com.dosprint.core.ErrorRecord
import com.dosprint.core.ErrorReporter
import com.dosprint.core.Outcome
import com.dosprint.data.render.SheetPreviewRenderer
import com.dosprint.domain.model.AndroidPrintServiceCapabilities
import com.dosprint.domain.model.DocumentKind
import com.dosprint.domain.model.PrintDocument
import com.dosprint.domain.model.PrintSettings
import com.dosprint.domain.model.PrinterProfile
import com.dosprint.ui.components.PreviousCrashDialog
import com.dosprint.ui.screens.DocumentsScreen
import com.dosprint.ui.screens.HomeScreen
import com.dosprint.ui.screens.LogsScreen
import com.dosprint.ui.screens.PreviewScreen
import com.dosprint.ui.screens.SettingsScreen
import com.dosprint.ui.screens.SetupScreen
import com.dosprint.ui.theme.DosPrintTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val session by lazy { SessionStore() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val container = (application as DosPrintApplication).container
        setContent {
            DosPrintApp(
                session = session,
                container = container,
                initialUris = extractUris(intent),
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun extractUris(intent: Intent?): List<String> {
        if (intent == null) {
            return emptyList()
        }
        val uris = LinkedHashSet<String>()
        intent.data?.let { uris.add(it.toString()) }
        intent.getStringArrayListExtra(Intent.EXTRA_STREAM)?.forEach { uris.add(it) }
        if (intent.action == Intent.ACTION_SEND) {
            readParcelable<Uri>(intent, Intent.EXTRA_STREAM)?.let { uris.add(it.toString()) }
        }
        return uris.toList()
    }

    private inline fun <reified T : Parcelable> readParcelable(intent: Intent, key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(key)
        }
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun DosPrintApp(
    session: SessionStore,
    container: com.dosprint.di.AppContainer,
    initialUris: List<String>,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val preferences by container.preferencesRepository.preferences
        .collectAsStateWithLifecycle(initialValue = container.defaultPreferences())
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val documentsViewModel: DocumentsViewModel = viewModel(
        factory = DocumentsViewModel.Factory(container, session),
    )
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(container))
    val setupViewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory(container, session))
    val previewRenderer = remember(context) { SheetPreviewRenderer(context) }
    val previewViewModel: PreviewViewModel = viewModel(
        factory = PreviewViewModel.Factory(container, session, previewRenderer),
    )
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            container = container,
            previewCache = container.previewCache,
            notificationPermissionChecker = ContextNotificationPermissionChecker(context),
        ),
    )

    val docsState by documentsViewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val setupState by setupViewModel.state.collectAsStateWithLifecycle()
    val previewState by previewViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()

    val themePreferences = preferences
    val backdrop = rememberLayerBackdrop()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        AppLog.info("picker: documents returned ${uris.size}")
        documentsViewModel.onImported(uris.map(Uri::toString), asImages = false)
    }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        AppLog.info("picker: images returned ${uris.size}")
        documentsViewModel.onImported(uris.map(Uri::toString), asImages = true)
    }

    var pendingPdfExport by remember { mutableStateOf<PendingPdfExport?>(null) }
    val activityScope = rememberCoroutineScope()
    val pdfExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val pending = pendingPdfExport
        pendingPdfExport = null
        if (uri != null && pending != null) {
            activityScope.launch {
                val outcome = withContext(container.dispatchers.io) {
                    container.pdfExportManager.export(
                        destination = uri,
                        documents = pending.documents,
                        requestedSettings = pending.settings,
                        lowMemory = container.isLowMemoryMode(),
                    )
                }
                when (outcome) {
                    is Outcome.Success -> snackbarHostState.showSnackbar(context.getString(R.string.pdf_saved))
                    is Outcome.Failure -> snackbarHostState.showSnackbar(outcome.error.message(context))
                }
            }
        }
    }
    val requestPdfSave: (List<PrintDocument>, PrintSettings) -> Unit = { documents, settings ->
        pendingPdfExport = PendingPdfExport(documents, settings)
        pdfExportPicker.launch(suggestedPdfFileName(documents))
    }
    val requestAndroidPrint: (List<PrintDocument>, PrintSettings) -> Unit = { documents, settings ->
        activityScope.launch {
            when (
                val result = container.printRepository.submit(
                    activity = activity,
                    documents = documents,
                    printer = PrinterProfile.AndroidPrintService,
                    capabilities = AndroidPrintServiceCapabilities,
                    settings = settings,
                    jobId = java.util.UUID.randomUUID().toString(),
                )
            ) {
                is Outcome.Success -> snackbarHostState.showSnackbar(context.getString(R.string.message_android_print_opened))
                is Outcome.Failure -> snackbarHostState.showSnackbar(result.error.message(context))
            }
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> settingsViewModel.onNotificationPermissionResult(granted) }

    LaunchedEffect(initialUris) {
        if (initialUris.isNotEmpty()) {
            documentsViewModel.onImported(initialUris, asImages = false)
        }
    }

    DosPrintTheme(
        themeOption = themePreferences.theme,
        useDynamicColor = themePreferences.useDynamicColor,
    ) {
        val windowSizeClass = calculateWindowSizeClass(activity)

        LaunchedEffect(currentRoute) {
            AppLog.info("navigation: now on ${currentRoute ?: "none"}")
        }
        LaunchedEffect(currentRoute) {
            if ((currentRoute == Route.Setup || currentRoute == Route.Preview) &&
                session.selection.value.documents.isEmpty()
            ) {
                documentsViewModel.clearSelection()
                navController.navigateHome()
            }
        }

        var previousCrash by remember { mutableStateOf<ErrorRecord?>(null) }
        LaunchedEffect(Unit) {
            previousCrash = withContext(Dispatchers.IO) { ErrorReporter.takePendingCrash() }
        }
        previousCrash?.let { record ->
            PreviousCrashDialog(
                record = record,
                onViewLogs = {
                    previousCrash = null
                    navController.navigate(Route.Logs)
                },
                onDismiss = { previousCrash = null },
            )
        }

        // Two separate effects, not one: a `collect` on an endless flow never returns, so a second
        // one after it would never be reached.
        //
        // The destination is read from the controller at emit time rather than captured. Capturing
        // the composable's `currentRoute` froze whatever it was when this effect started — `null` on
        // the first frame, before the back stack has an entry — and the guard then never matched, so
        // picking a file never navigated anywhere.
        LaunchedEffect(navController) {
            documentsViewModel.imported.collect { event ->
                if (event.count <= 0) return@collect
                if (shouldOpenAfterImport(navController.currentDestination())) {
                    setupViewModel.selectMode(if (event.asImages) PrintMode.IMAGES else PrintMode.DOCUMENTS)
                    navController.navigate(Route.Setup)
                }
            }
        }
        LaunchedEffect(navController) {
            documentsViewModel.importFailures.collect {
                if (shouldOpenAfterImport(navController.currentDestination())) {
                    navController.navigate(Route.Documents)
                }
            }
        }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { scaffoldPadding ->
            val glassEnabled = preferences.liquidGlassEnabled
            val screenPadding = rememberScreenPadding(
                content = scaffoldPadding,
                horizontal = screenGutter(windowSizeClass.isExpandedWidth()),
                bottom = if (glassEnabled) 88.dp else 104.dp,
            )
            Box(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxSize().then(
                        if (glassEnabled) Modifier.layerBackdrop(backdrop) else Modifier,
                    ),
                ) {
                    DosPrintNavigationRail(
                        currentRoute = currentRoute,
                        windowSizeClass = windowSizeClass,
                        onNavigate = {
                            navigateTopDestination(
                                navController = navController,
                                destination = it,
                                session = session,
                                documentsViewModel = documentsViewModel,
                            )
                        },
                    )
                    DosPrintNavHost(
                        navController = navController,
                        contentPadding = screenPadding,
                        states = ScreenStates(
                            home = homeState,
                            documents = docsState,
                            setup = setupState,
                            preview = previewState,
                            settings = settingsState,
                        ),
                        callbacks = ScreenCallbacks(
                            actions = ScreenActions(
                                onHome = homeViewModel::acceptAction,
                                onHomeDocumentClicked = { document ->
                                    session.setDocuments(listOf(document))
                                    setupViewModel.selectMode(
                                        if (document.format.kind == DocumentKind.IMAGE) {
                                            PrintMode.IMAGES
                                        } else {
                                            PrintMode.DOCUMENTS
                                        },
                                    )
                                    navController.navigate(Route.Setup)
                                },
                                onDocuments = documentsViewModel::acceptAction,
                                onSetup = setupViewModel::dispatch,
                                onImagePageChange = setupViewModel::setImagePage,
                                onPreview = previewViewModel::dispatch,
                                onSettings = settingsViewModel::acceptAction,
                            ),
                            launchers = ScreenLaunchers(
                                onPickFiles = { filePicker.launch(DocumentMimeTypes) },
                                onPickImages = { mediaPicker.launch(ImageMimeTypes) },
                                onPickMedia = { mediaPicker.launch(ImageMimeTypes) },
                                onSavePdf = requestPdfSave,
                                onPrintAndroid = requestAndroidPrint,
                                onRequestNotificationPermission = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                                onSetupSurfaceWidth = setupViewModel::onPreviewSurface,
                                onPreviewSurfaceWidth = previewViewModel::onSurfaceWidth,
                            ),
                        ),
                    )
                }
                if (docsState.isImporting) {
                    val importingLabel = stringResource(R.string.importing_files)
                    LinearProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .semantics { contentDescription = importingLabel },
                    )
                }
                DosPrintBottomBar(
                    currentRoute = currentRoute,
                    windowSizeClass = windowSizeClass,
                    onNavigate = {
                        navigateTopDestination(
                            navController = navController,
                            destination = it,
                            session = session,
                            documentsViewModel = documentsViewModel,
                        )
                    },
                    backdrop = backdrop,
                    liquidGlassEnabled = glassEnabled,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private val DocumentMimeTypes = arrayOf("*/*")
private val ImageMimeTypes = arrayOf("image/*")

private data class PendingPdfExport(
    val documents: List<PrintDocument>,
    val settings: PrintSettings,
)

private fun suggestedPdfFileName(documents: List<PrintDocument>): String {
    val sourceName = documents.singleOrNull()?.displayName ?: return "Dos Print.pdf"
    val baseName = sourceName
        .substringBeforeLast('.', missingDelimiterValue = sourceName)
        .replace(Regex("[/\\\\:*?\"<>|]"), "_")
        .trim()
        .takeIf(String::isNotEmpty)
        ?: "Dos Print"
    return "$baseName.pdf"
}

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
private fun DosPrintNavHost(
    navController: NavHostController,
    contentPadding: PaddingValues,
    states: ScreenStates,
    callbacks: ScreenCallbacks,
) {
    val homeState = states.home
    val docsState = states.documents
    val setupState = states.setup
    val previewState = states.preview
    val settingsState = states.settings
    val actions = callbacks.actions
    val launchers = callbacks.launchers
    val onHomeAction = actions.onHome
    val onHomeDocumentClicked = actions.onHomeDocumentClicked
    val onDocumentsAction = actions.onDocuments
    val onSetupAction = actions.onSetup
    val onImagePageChange = actions.onImagePageChange
    val onPreviewAction = actions.onPreview
    val onSettingsAction = actions.onSettings
    val onPreviewSurfaceWidth = launchers.onPreviewSurfaceWidth
    val onPickFiles = launchers.onPickFiles
    val onPickImages = launchers.onPickImages
    val onPickMedia = launchers.onPickMedia
    val onRequestNotificationPermission = launchers.onRequestNotificationPermission
    val onSetupPreviewSurface = launchers.onSetupSurfaceWidth
    val onSavePdf = launchers.onSavePdf
    val onPrintAndroid = launchers.onPrintAndroid
    NavHost(
        navController = navController,
        startDestination = Route.Home,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Route.Home) {
            HomeScreen(
                state = homeState,
                onAction = { action ->
                    when (action) {
                        HomeAction.OpenDocuments -> navController.navigate(Route.Documents)
                        HomeAction.PickDocuments -> onPickFiles()
                        HomeAction.PickImages -> onPickImages()
                        is HomeAction.DocumentClicked -> onHomeDocumentClicked(action.document)
                        else -> onHomeAction(action)
                    }
                },
                contentPadding = contentPadding,
            )
        }
        composable(Route.Documents) {
            DocumentsScreen(
                state = docsState,
                onAction = { action ->
                    when (action) {
                        DocumentsAction.BrowseFiles -> onPickFiles()
                        DocumentsAction.OpenLogs -> navController.navigate(Route.Logs)
                        DocumentsAction.Continue -> navController.navigate(Route.Setup)
                        else -> onDocumentsAction(action)
                    }
                },
                contentPadding = contentPadding,
            )
        }
        composable(Route.Setup) {
            SetupScreen(
                state = setupState,
                onAction = { action ->
                    when (action) {
                        SetupAction.OpenPreview -> navController.navigate(Route.Preview)
                        SetupAction.SavePdf -> onSavePdf(setupState.documents, setupState.settings)
                        SetupAction.Print -> onPrintAndroid(setupState.documents, setupState.settings)
                        SetupAction.OpenLogs -> navController.navigate(Route.Logs)
                        else -> onSetupAction(action)
                    }
                },
                onPreviewSurface = onSetupPreviewSurface,
                onImagePage = onImagePageChange,
                contentPadding = contentPadding,
            )
        }
        composable(Route.Preview) {
            PreviewScreen(
                state = previewState,
                onAction = { action ->
                    when (action) {
                        PreviewAction.BackToSetup, PreviewAction.OpenSetup ->
                            navController.popBackStack(Route.Setup, inclusive = false)
                        PreviewAction.SavePdf -> onSavePdf(previewState.documents, previewState.settings)
                        PreviewAction.Print -> onPrintAndroid(previewState.documents, previewState.settings)
                        else -> onPreviewAction(action)
                    }
                },
                onSurfaceWidth = onPreviewSurfaceWidth,
                contentPadding = contentPadding,
            )
        }
        composable(Route.Settings) {
            SettingsScreen(
                state = settingsState,
                onAction = { action ->
                    when (action) {
                        SettingsAction.RequestNotificationsPermission -> onRequestNotificationPermission()
                        else -> onSettingsAction(action)
                    }
                },
                onOpenLogs = { navController.navigate(Route.Logs) },
                contentPadding = contentPadding,
            )
        }
        composable(Route.Logs) {
            LogsScreen(
                onBack = { navController.popBackStack() },
                contentPadding = contentPadding,
            )
        }
    }
}

private fun NavHostController.navigateTop(destination: TopLevelDestination) {
    if (destination == TopLevelDestination.HOME) {
        navigateHome()
        return
    }
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

private fun NavHostController.navigateHome() {
    navigate(Route.Home) {
        popUpTo(graph.startDestinationId) {
            inclusive = false
            saveState = false
        }
        launchSingleTop = true
        restoreState = false
    }
}

private fun navigateTopDestination(
    navController: NavHostController,
    destination: TopLevelDestination,
    session: SessionStore,
    documentsViewModel: DocumentsViewModel,
) {
    if (destination == TopLevelDestination.HOME) {
        session.clearDocuments()
        documentsViewModel.clearSelection()
    }
    navController.navigateTop(destination)
}

class ContextNotificationPermissionChecker(
    private val context: Context,
) : NotificationPermissionChecker {
    override fun isGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No Activity found in Context")
}

/**
 * The destination currently on screen, read from the controller rather than from composable state.
 *
 * Reading a `State` captured at effect start gives a stale value: the back stack has no entry on the
 * first frame, so a guard written against it never matches and the navigation it gates silently
 * never happens.
 */
private fun NavHostController.currentDestination(): String? =
    currentBackStackEntry?.destination?.route
