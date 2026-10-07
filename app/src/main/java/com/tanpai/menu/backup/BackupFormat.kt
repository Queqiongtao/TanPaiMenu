package com.tanpai.menu.backup

import com.tanpai.menu.data.db.DishHighlight
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class BackupCategory(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val enabled: Boolean,
    val updatedAt: Long
)

@Serializable
data class BackupDish(
    val id: Long,
    val categoryId: Long,
    val name: String,
    val priceCents: Int,
    val unit: String,
    val imagePath: String? = null,
    val stockEnabled: Boolean = false,
    val stockCount: Int = 0,
    val soldOut: Boolean = false,
    val highlight: DishHighlight? = null,
    val description: String = "",
    val sortOrder: Int = 0,
    val enabled: Boolean = true,
    val updatedAt: Long
)

@Serializable
data class BackupFile(
    val format: Int,
    val exportedAt: String,
    val app: String,
    val categories: List<BackupCategory>,
    val dishes: List<BackupDish>,
    val images: List<String>
)

sealed class BackupException(message: String) : Exception(message) {
    class IncompatibleFormat(val format: Int) : BackupException("backup format $format is not supported")
    class Corrupt(val detail: String) : BackupException("corrupt backup: $detail")
}

enum class BackupFailure { NotFound, UnsupportedFormat, Corrupt, Io }

sealed interface BackupResult {
    data class Success(val categories: Int, val dishes: Int, val images: Int) : BackupResult
    data class Failure(val failure: BackupFailure, val detail: String = "") : BackupResult
}

object BackupCodec {

    const val FORMAT = 2
    const val APP_NAME = "TanPaiMenu"
    const val FILE_NAME = "backup.json"
    const val IMAGES_DIR = "images"

    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun nowIsoUtc(clock: () -> Long = System::currentTimeMillis): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(clock()))
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    fun decode(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    fun validate(file: BackupFile) {
        if (file.format > FORMAT) throw BackupException.IncompatibleFormat(file.format)
        if (file.format < 1) throw BackupException.Corrupt("format=${file.format}")
        if (file.app != APP_NAME) throw BackupException.Corrupt("app=${file.app}")
        val categoryIds = file.categories.map { it.id }.toSet()
        file.dishes.firstOrNull { it.categoryId !in categoryIds }?.let {
            throw BackupException.Corrupt("dish ${it.id} references missing category ${it.categoryId}")
        }
    }
}
