package com.dosprint

import android.app.Application
import com.dosprint.core.AppLog
import com.dosprint.core.CrashHandler
import com.dosprint.core.CrashStore
import com.dosprint.core.ErrorReporter
import com.dosprint.core.ExitReasonReporter
import com.dosprint.core.FileLog
import com.dosprint.di.AppContainer
import java.io.File

class DosPrintApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: android.content.Context?) {
        super.attachBaseContext(base)
        installCrashHandler()
    }

    override fun onCreate() {
        super.onCreate()
        // Logging and error reporting come up before anything else. If building the container is
        // what fails, there has to be somewhere for that failure to be recorded.
        FileLog.initialize(File(filesDir, LOGS_DIRECTORY))
        ErrorReporter.initialize(File(filesDir, ERRORS_DIRECTORY))
        AppLog.info("Dos Print ${BuildConfig.VERSION_NAME} starting")
        ExitReasonReporter.reportNew(this, File(filesDir, ERRORS_DIRECTORY))
        container = AppContainer.get(this)
        container.warmUp()
        AppLog.info("Container ready")
    }

    override fun onTerminate() {
        container.release()
        super.onTerminate()
    }

    val crashStore: CrashStore get() = container.crashStore

    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is CrashHandler) {
            return
        }
        Thread.setDefaultUncaughtExceptionHandler(
            CrashHandler(
                store = NoOpCrashStore,
                delegate = previous,
            ),
        )
    }
}

private const val ERRORS_DIRECTORY = "errors"
private const val LOGS_DIRECTORY = "logs"

private object NoOpCrashStore : CrashStore {
    override fun record(throwable: Throwable) {
        AppLog.error("Unhandled crash before container init", throwable)
    }

    override fun latest(): com.dosprint.data.local.CrashRecord? = null
}
