package com.dosprint.di

import android.content.Context
import com.dosprint.core.CrashStore
import com.dosprint.core.DefaultDispatchers
import com.dosprint.core.DispatcherProvider
import com.dosprint.core.InMemoryCrashStore
import com.dosprint.data.documents.DocumentPageCounter
import com.dosprint.data.documents.DocumentRepositoryImpl
import com.dosprint.data.history.HistoryRepositoryImpl
import com.dosprint.data.local.DocumentFile
import com.dosprint.data.local.HistoryFile
import com.dosprint.data.local.JsonFileStore
import com.dosprint.data.local.PresetFile
import com.dosprint.data.local.PrinterFile
import com.dosprint.data.media.MediaImagePool
import com.dosprint.data.preferences.PreferencesRepositoryImpl
import com.dosprint.data.presets.PresetRepositoryImpl
import com.dosprint.data.print.PdfExportManager
import com.dosprint.data.print.PrintJobCoordinator
import com.dosprint.data.print.PrintJobMonitor
import com.dosprint.data.print.PrintJobStateSink
import com.dosprint.data.print.PrintRepositoryImpl
import com.dosprint.data.printer.MdnsDiscovery
import com.dosprint.data.printer.PrinterRepositoryImpl
import com.dosprint.data.printer.SystemPrinterGateway
import com.dosprint.data.render.PreviewBitmapCache
import com.dosprint.data.render.SheetPreviewRenderer
import com.dosprint.domain.repository.DocumentRepository
import com.dosprint.domain.repository.HistoryRepository
import com.dosprint.domain.repository.PreferencesRepository
import com.dosprint.domain.repository.PresetRepository
import com.dosprint.domain.repository.PrintRepository
import com.dosprint.domain.repository.PrinterRepository
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class AppContainer private constructor(
    val applicationContext: Context,
    val dispatchers: DispatcherProvider,
) {
    val applicationScope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineName("dos-print-app"),
    )

    val crashStore: CrashStore = InMemoryCrashStore()

    val preferencesRepository: PreferencesRepository = PreferencesRepositoryImpl(applicationContext)

    private val documentStore = JsonFileStore(
        file = File(applicationContext.filesDir, "settings/documents.json"),
        serializer = DocumentFile.serializer(),
        emptyValue = DocumentFile(),
    )

    private val printerStore = JsonFileStore(
        file = File(applicationContext.filesDir, "settings/printers.json"),
        serializer = PrinterFile.serializer(),
        emptyValue = PrinterFile(),
    )

    private val historyStore = JsonFileStore(
        file = File(applicationContext.filesDir, "history/jobs.json"),
        serializer = HistoryFile.serializer(),
        emptyValue = HistoryFile(),
    )

    private val presetStore = JsonFileStore(
        file = File(applicationContext.filesDir, "presets/presets.json"),
        serializer = PresetFile.serializer(),
        emptyValue = PresetFile(),
    )

    private val pageCounter = DocumentPageCounter(applicationContext)

    val documentRepository: DocumentRepository = DocumentRepositoryImpl(
        context = applicationContext,
        dispatchers = dispatchers,
        pageCounter = pageCounter,
        store = documentStore,
    )

    val presetRepository: PresetRepository = PresetRepositoryImpl(store = presetStore)

    val historyRepository: HistoryRepository = HistoryRepositoryImpl(
        store = historyStore,
        historyLimitProvider = { currentHistoryLimit },
        keepHistoryProvider = { keepHistoryEnabled },
    )

    private val printerGateway = SystemPrinterGateway(applicationContext, dispatchers)

    val printerDiscovery = MdnsDiscovery(applicationContext)

    val previewCache = PreviewBitmapCache()

    val mediaImagePool: MediaImagePool = MediaImagePool(applicationContext)

    val sheetPreviewRenderer: SheetPreviewRenderer = SheetPreviewRenderer(applicationContext)

    val printerRepository: PrinterRepository = PrinterRepositoryImpl(
        gateway = printerGateway,
        discovery = printerDiscovery,
        preferencesRepository = preferencesRepository,
        store = printerStore,
    )

    private val jobStateSink = PrintJobStateSink(historyRepository, applicationScope)

    private val jobCoordinator = PrintJobCoordinator(applicationContext, jobStateSink)

    val pdfExportManager = PdfExportManager(applicationContext, jobCoordinator)

    private val jobMonitor = PrintJobMonitor(
        dispatchers = dispatchers,
        scope = applicationScope,
        jobSource = { printerGateway.currentJobs() },
    )

    val printRepository: PrintRepository = PrintRepositoryImpl(
        dispatchers = dispatchers,
        historyRepository = historyRepository,
        coordinator = jobCoordinator,
        scope = applicationScope,
        monitor = jobMonitor,
        documentTouchProvider = { uris -> documentRepository.touchPrinted(uris) },
        lowMemoryProvider = { lowMemoryMode },
    ).also { repository ->
        jobMonitor.register(repository)
    }

    @Volatile
    private var currentHistoryLimit = DEFAULT_HISTORY_LIMIT

    @Volatile
    private var keepHistoryEnabled = true

    @Volatile
    private var lowMemoryMode = false

    /** Whether the device is in low memory mode, which caps raster sizes during printing. */
    fun isLowMemoryMode(): Boolean = lowMemoryMode

    fun defaultPreferences(): com.dosprint.domain.model.UserPreferences = cachedPreferences

    @Volatile
    private var cachedPreferences = com.dosprint.domain.model.UserPreferences()

    fun warmUp() {
        applicationScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                cachedPreferences = preferences
                currentHistoryLimit = preferences.historyLimit
                keepHistoryEnabled = preferences.keepJobHistory
                lowMemoryMode = preferences.lowMemoryMode
            }
        }
        applicationScope.launch {
            loadStores()
            printRepository.resumeMonitoring()
            printRepository.reconcile()
            if (preferencesRepository.preferences.first().autoDiscoverPrinters) {
                printerRepository.refreshPrinters()
            }
        }
    }

    private suspend fun loadStores() {
        documentRepository.load()
        presetRepository.load()
        historyRepository.load()
        printerRepository.load()
    }

    fun release() {
        jobCoordinator.shutdown()
        (printRepository as? PrintRepositoryImpl)?.release()
        jobMonitor.unregister()
    }

    companion object {
        private const val DEFAULT_HISTORY_LIMIT = 100

        @Volatile
        private var instance: AppContainer? = null

        fun get(context: Context): AppContainer = instance ?: synchronized(this) {
            instance ?: AppContainer(
                applicationContext = context.applicationContext,
                dispatchers = DefaultDispatchers,
            ).also { instance = it }
        }
    }
}
