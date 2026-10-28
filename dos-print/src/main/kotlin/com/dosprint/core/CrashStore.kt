package com.dosprint.core

import com.dosprint.data.local.CrashRecord

interface CrashStore {
    fun record(throwable: Throwable)

    fun latest(): CrashRecord?
}

class InMemoryCrashStore : CrashStore {
    @Volatile
    private var record: CrashRecord? = null

    override fun record(throwable: Throwable) {
        record = CrashRecord(
            summary = throwable.javaClass.simpleName,
            stackTrace = throwable.stackTraceToString(),
            timestampMillis = System.currentTimeMillis(),
        )
    }

    override fun latest(): CrashRecord? = record

    fun clear() {
        record = null
    }
}
