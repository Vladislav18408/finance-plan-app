package com.vladk.financeplan.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

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

    fun update(transform: (AppData) -> AppData) {
        var snapshot: AppData
        while (true) {
            val current = _data.value
            snapshot = transform(current)
            if (_data.compareAndSet(current, snapshot)) break
        }
        scope.launch { writeLock.withLock { persist(_data.value) } }
    }

    fun replaceAll(newData: AppData) = update { newData }

    fun exportJson(): String = json.encodeToString(AppData.serializer(), _data.value)

    /** Parses a backup. Throws if the text is not a valid backup. */
    fun parseBackup(text: String): AppData = json.decodeFromString(AppData.serializer(), text)

    private fun load(): AppData {
        val loaded = try {
            if (file.exists()) json.decodeFromString(AppData.serializer(), file.readText()) else AppData()
        } catch (e: Exception) {
            // Keep the unreadable file aside instead of overwriting it.
            file.copyTo(File(file.parentFile, "finance-data.broken.json"), overwrite = true)
            AppData()
        }
        // First start: add the predefined categories (with no amounts – the user fills them in).
        return if (loaded.presetsVersion == 0 && loaded.categories.isEmpty()) {
            loaded.copy(categories = Presets.defaults(), presetsVersion = Presets.VERSION)
        } else {
            loaded
        }
    }

    private fun persist(value: AppData) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(AppData.serializer(), value))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }
    }
}
