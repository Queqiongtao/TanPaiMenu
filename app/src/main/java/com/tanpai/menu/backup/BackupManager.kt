package com.tanpai.menu.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.logging.TpLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BackupManager(
    private val context: Context,
    private val repository: MenuRepository,
    private val imageStore: ImageStore,
    private val clock: () -> Long = System::currentTimeMillis
) {

    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun exportTo(treeUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentTree(resolver, treeUri)
            val menu = repository.exportAll()
            val imagePaths = menu.dishes.mapNotNull { it.imagePath }.distinct()
            val backup = BackupFile(
                format = BackupCodec.FORMAT,
                exportedAt = BackupCodec.nowIsoUtc(clock),
                app = BackupCodec.APP_NAME,
                categories = menu.categories.map { it.toBackup() },
                dishes = menu.dishes.map { it.toBackup() },
                images = imagePaths
            )
            tree.writeFile(tree.rootUri, BackupCodec.FILE_NAME, BackupCodec.encode(backup))
                ?: return@withContext BackupResult.Failure(BackupFailure.Io, BackupCodec.FILE_NAME)

            val images = imagePaths.mapNotNull { path ->
                val bytes = imageStore.readBytes(path) ?: return@mapNotNull null
                path.substringAfterLast('/') to bytes
            }
            var copied = 0
            if (images.isNotEmpty()) {
                val imagesDir = tree.ensureDirectory(tree.rootUri, BackupCodec.IMAGES_DIR)
                    ?: return@withContext BackupResult.Failure(BackupFailure.Io, BackupCodec.IMAGES_DIR)
                images.forEach { (name, bytes) ->
                    if (tree.writeFile(imagesDir, name, bytes) != null) copied++
                }
            }
            TpLog.i(
                "backup",
                "导出完成：分类=${menu.categories.size} 菜品=${menu.dishes.size} 图片=$copied 目录=$treeUri"
            )
            BackupResult.Success(menu.categories.size, menu.dishes.size, copied)
        } catch (t: Throwable) {
            TpLog.e("backup", "导出失败", t)
            BackupResult.Failure(BackupFailure.Io, t.message ?: t::class.java.simpleName)
        }
    }

    suspend fun importFrom(treeUri: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentTree(resolver, treeUri)
            val source = tree.child(tree.rootUri, BackupCodec.FILE_NAME)
                ?: return@withContext BackupResult.Failure(BackupFailure.NotFound, BackupCodec.FILE_NAME)
            val text = tree.readText(source.uri)
                ?: return@withContext BackupResult.Failure(BackupFailure.Io, BackupCodec.FILE_NAME)
            val backup = try {
                BackupCodec.decode(text)
            } catch (t: Exception) {
                TpLog.e("backup", "备份解析失败", t)
                return@withContext BackupResult.Failure(BackupFailure.Corrupt, t.message ?: "")
            }
            try {
                BackupCodec.validate(backup)
            } catch (e: BackupException.IncompatibleFormat) {
                TpLog.w("backup", "备份版本不兼容：format=${backup.format}")
                return@withContext BackupResult.Failure(BackupFailure.UnsupportedFormat, e.message ?: "")
            } catch (e: BackupException) {
                TpLog.w("backup", "备份校验失败：${e.message}")
                return@withContext BackupResult.Failure(BackupFailure.Corrupt, e.message ?: "")
            }

            repository.replaceAll(
                backup.categories.map { it.toEntity() },
                backup.dishes.map { it.toEntity() }
            )

            var restored = 0
            tree.child(tree.rootUri, BackupCodec.IMAGES_DIR)?.let { dir ->
                tree.childrenOf(dir.uri).forEach { child ->
                    val bytes = tree.readBytes(child.uri) ?: return@forEach
                    if (imageStore.writeImage(child.name, bytes) != null) restored++
                }
            }
            TpLog.i(
                "backup",
                "导入完成：分类=${backup.categories.size} 菜品=${backup.dishes.size} 图片=$restored 目录=$treeUri"
            )
            BackupResult.Success(backup.categories.size, backup.dishes.size, restored)
        } catch (t: Throwable) {
            TpLog.e("backup", "导入失败", t)
            BackupResult.Failure(BackupFailure.Io, t.message ?: t::class.java.simpleName)
        }
    }
}

internal fun Category.toBackup(): BackupCategory =
    BackupCategory(id = id, name = name, sortOrder = sortOrder, enabled = enabled, updatedAt = updatedAt)

internal fun Dish.toBackup(): BackupDish = BackupDish(
    id = id,
    categoryId = categoryId,
    name = name,
    priceCents = priceCents,
    unit = unit,
    imagePath = imagePath,
    stockEnabled = stockEnabled,
    stockCount = stockCount,
    soldOut = soldOut,
    highlight = highlight,
    description = description,
    sortOrder = sortOrder,
    enabled = enabled,
    updatedAt = updatedAt
)

internal fun BackupCategory.toEntity(): Category =
    Category(id = id, name = name, sortOrder = sortOrder, enabled = enabled, updatedAt = updatedAt)

internal fun BackupDish.toEntity(): Dish = Dish(
    id = id,
    categoryId = categoryId,
    name = name,
    priceCents = priceCents,
    unit = unit,
    imagePath = imagePath,
    stockEnabled = stockEnabled,
    stockCount = stockCount,
    soldOut = soldOut,
    highlight = highlight,
    description = description,
    sortOrder = sortOrder,
    enabled = enabled,
    updatedAt = updatedAt
)

internal data class DocumentEntry(val uri: Uri, val name: String, val mimeType: String)

internal class DocumentTree(private val resolver: ContentResolver, private val treeUri: Uri) {

    private val treeDocumentId: String = DocumentsContract.getTreeDocumentId(treeUri)

    val rootUri: Uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocumentId)

    fun childrenOf(documentUri: Uri): List<DocumentEntry> {
        val documentId = DocumentsContract.getDocumentId(documentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val entries = mutableListOf<DocumentEntry>()
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += DocumentEntry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0)),
                    name = cursor.getString(1) ?: "",
                    mimeType = cursor.getString(2) ?: ""
                )
            }
        }
        return entries
    }

    fun child(parent: Uri, name: String): DocumentEntry? =
        childrenOf(parent).firstOrNull { it.name == name }

    fun writeFile(parent: Uri, name: String, bytes: ByteArray): Uri? {
        val uri = ensureFile(parent, name) ?: return null
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: return null
        return uri
    }

    fun writeFile(parent: Uri, name: String, text: String): Uri? =
        writeFile(parent, name, text.toByteArray(Charsets.UTF_8))

    fun ensureDirectory(parent: Uri, name: String): Uri? {
        child(parent, name)?.let { existing ->
            return if (existing.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) existing.uri else null
        }
        return DocumentsContract.createDocument(
            resolver,
            parent,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        )
    }

    fun readText(uri: Uri): String? = readBytes(uri)?.let { String(it, Charsets.UTF_8) }

    fun readBytes(uri: Uri): ByteArray? = resolver.openInputStream(uri)?.use { it.readBytes() }

    private fun ensureFile(parent: Uri, name: String): Uri? {
        child(parent, name)?.let { existing ->
            if (existing.mimeType != DocumentsContract.Document.MIME_TYPE_DIR) return existing.uri
            DocumentsContract.deleteDocument(resolver, existing.uri)
        }
        return DocumentsContract.createDocument(resolver, parent, MIME_JSON, name)
    }

    private companion object {
        const val MIME_JSON = "application/json"
    }
}
