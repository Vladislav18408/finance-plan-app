package com.vladk.financeplan.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

/**
 * Keeps [AppData] in memory and mirrors every change to a JSON file in the app's private storage.
 * The file is written to a temp file first and then renamed, so a crash never leaves half a file.
 */
class Repository(context: Context) {

    private val file = File(context.filesDir, "finance-data.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    /** True while the last attempt to write the file failed (e.g. the phone's storage is full). */
    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed.asStateFlow()

    fun update(transform: (AppData) -> AppData) {
        while (true) {
            val current = _data.value
            if (_data.compareAndSet(current, transform(current))) break
        }
        scope.launch {
            writeLock.withLock {
                // Never let a failed write crash the app; the data is still in memory and the
                // next change tries again.
                _saveFailed.value = runCatching { persist(_data.value) }
                    .onFailure { Log.e("FinancePlan", "Could not save the data file", it) }
                    .isFailure
            }
        }
    }

    fun exportJson(): String = Backup.encode(_data.value)

    private fun load(): AppData {
        val loaded = try {
            if (file.exists()) Backup.json.decodeFromString(AppData.serializer(), file.readText()) else AppData()
        } catch (e: Exception) {
            // Keep the unreadable file aside instead of overwriting it.
            runCatching { file.copyTo(File(file.parentFile, "finance-data.broken.json"), overwrite = true) }
            AppData()
        }
        return Backup.clean(withPresets(loaded)).data
    }

    private fun persist(value: AppData) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tmp).use { out ->
            out.write(Backup.encode(value).toByteArray())
            out.fd.sync() // on disk before it replaces the old file
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw java.io.IOException("Could not replace ${file.name}")
        }
    }

    companion object {
        /**
         * The saved data, read straight from the file – for code that runs without the app being
         * open (the reminder). Throws when there is no readable file yet.
         */
        fun readOnce(context: Context): AppData {
            val text = File(context.filesDir, "finance-data.json").readText()
            return Backup.clean(Backup.json.decodeFromString(AppData.serializer(), text)).data
        }

        /** First start: add the predefined categories (with no amounts – the user fills them in). */
        fun withPresets(data: AppData): AppData =
            if (data.presetsVersion == 0 && data.categories.isEmpty()) {
                data.copy(categories = Presets.defaults(), presetsVersion = Presets.VERSION)
            } else {
                data
            }
    }
}
