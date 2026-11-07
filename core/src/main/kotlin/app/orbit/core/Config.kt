package app.orbit.core

import java.io.File
import java.util.Properties

interface ConfigStore {
    fun load(): Map<String, String>
    fun save(values: Map<String, String>)
}

class InMemoryConfigStore : ConfigStore {
    private var data: Map<String, String> = emptyMap()
    override fun load() = data
    override fun save(values: Map<String, String>) { data = values.toMap() }
}

/** Plain properties file, written atomically. Holds no secrets. */
class FileConfigStore(private val file: File) : ConfigStore {
    override fun load(): Map<String, String> = try {
        if (!file.exists()) emptyMap() else {
            val p = Properties(); file.inputStream().use { p.load(it) }
            p.stringPropertyNames().associateWith { p.getProperty(it) }
        }
    } catch (e: Exception) { emptyMap() }

    override fun save(values: Map<String, String>) {
        val p = Properties().apply { values.forEach { (k, v) -> setProperty(k, v) } }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.outputStream().use { p.store(it, "ORBIT config") }
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}
