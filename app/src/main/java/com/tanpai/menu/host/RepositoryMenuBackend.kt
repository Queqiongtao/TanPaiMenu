package com.tanpai.menu.host

import android.webkit.MimeTypeMap
import com.tanpai.menu.data.MenuRepository
import com.tanpai.menu.data.db.ConnectedDevice
import com.tanpai.menu.data.db.DeviceDao
import com.tanpai.menu.data.db.Dish
import com.tanpai.menu.data.db.DishHighlight
import com.tanpai.menu.domain.SellError
import com.tanpai.menu.domain.SellResult
import com.tanpai.menu.imaging.ImageStore
import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.CategoryPatchRequest
import com.tanpai.menu.net.protocol.DeviceDto
import com.tanpai.menu.net.protocol.DishCreateRequest
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.DishPatchRequest
import com.tanpai.menu.net.protocol.Iso8601
import com.tanpai.menu.net.protocol.StateDto
import com.tanpai.menu.net.protocol.toDto

/**
 * `MenuBackend` 的生产实现：把契约语义落到 `MenuRepository` 与 `ImageStore`（`docs/design/0015` 第 2.1 节）。
 *
 * 约束：所有写操作都经仓储的单写者事务（`version` +1 并广播事件），此处不直接操作菜品 / 分类 DAO。
 */
class RepositoryMenuBackend(
    private val repository: MenuRepository,
    private val imageStore: ImageStore,
    private val deviceDao: DeviceDao,
    private val storeName: () -> String,
    private val clientEditsEnabled: () -> Boolean,
    private val now: () -> Long = System::currentTimeMillis
) : MenuBackend {

    override suspend fun state(): StateDto = StateDto(
        version = repository.currentVersion(),
        categories = repository.enabledCategories().map { it.toDto() },
        dishes = repository.enabledDishes().map { it.toDto() },
        storeName = storeName(),
        clientEditsEnabled = clientEditsEnabled()
    )

    override suspend fun categories(): List<CategoryDto> =
        repository.enabledCategories().map { it.toDto() }

    override suspend fun createCategory(name: String, sortOrder: Int): CategoryDto {
        val id = repository.createCategory(name.trim(), sortOrder)
        return requireNotNull(repository.categoryById(id)).toDto()
    }

    override suspend fun patchCategory(
        id: Long,
        patch: CategoryPatchRequest
    ): WriteResult<CategoryDto> {
        val current = repository.categoryById(id) ?: return WriteResult.NotFound
        val updated = current.copy(
            name = patch.name?.trim() ?: current.name,
            sortOrder = patch.sortOrder ?: current.sortOrder,
            enabled = patch.enabled ?: current.enabled
        )
        return WriteResult.Ok(repository.saveCategory(updated).toDto())
    }

    override suspend fun deleteCategory(id: Long): WriteResult<Unit> {
        repository.categoryById(id) ?: return WriteResult.NotFound
        val imagePaths = repository.enabledDishes()
            .filter { it.categoryId == id }
            .mapNotNull { it.imagePath }
        repository.deleteCategory(id)
        imagePaths.forEach { imageStore.delete(it) }
        return WriteResult.Ok(Unit)
    }

    override suspend fun dishes(): List<DishDto> = repository.enabledDishes().map { it.toDto() }

    override suspend fun createDish(request: DishCreateRequest): WriteResult<DishDto> {
        repository.categoryById(request.categoryId) ?: return WriteResult.NotFound
        val id = repository.createDish(
            Dish(
                categoryId = request.categoryId,
                name = request.name.trim(),
                priceCents = request.priceCents,
                unit = request.unit,
                imagePath = request.imagePath?.takeIf { it.isNotBlank() },
                stockEnabled = request.stockEnabled,
                stockCount = request.stockCount,
                highlight = request.highlight,
                description = request.description,
                sortOrder = request.sortOrder,
                updatedAt = 0L
            )
        )
        return WriteResult.Ok(requireNotNull(repository.dishById(id)).toDto())
    }

    override suspend fun patchDish(id: Long, patch: DishPatchRequest): WriteResult<DishDto> {
        val current = repository.dishById(id) ?: return WriteResult.NotFound
        if (patch.categoryId != null && patch.categoryId != current.categoryId) {
            repository.categoryById(patch.categoryId) ?: return WriteResult.NotFound
        }
        val imagePath = when (val raw = patch.imagePath) {
            null -> current.imagePath
            "" -> null
            else -> raw
        }
        val highlight = when (val raw = patch.highlight) {
            null -> current.highlight
            "" -> null
            else -> DishHighlight.fromWire(raw) ?: return WriteResult.Conflict
        }
        val updated = current.copy(
            categoryId = patch.categoryId ?: current.categoryId,
            name = patch.name?.trim() ?: current.name,
            priceCents = patch.priceCents ?: current.priceCents,
            unit = patch.unit ?: current.unit,
            imagePath = imagePath,
            stockEnabled = patch.stockEnabled ?: current.stockEnabled,
            stockCount = patch.stockCount ?: current.stockCount,
            soldOut = patch.soldOut ?: current.soldOut,
            highlight = highlight,
            description = patch.description ?: current.description,
            sortOrder = patch.sortOrder ?: current.sortOrder,
            enabled = patch.enabled ?: current.enabled
        )
        val saved = repository.saveDish(updated)
        if (current.imagePath != null && current.imagePath != saved.imagePath) {
            imageStore.delete(current.imagePath)
        }
        return WriteResult.Ok(saved.toDto())
    }

    override suspend fun deleteDish(id: Long): WriteResult<Unit> {
        val current = repository.dishById(id) ?: return WriteResult.NotFound
        repository.deleteDish(id)
        current.imagePath?.let { imageStore.delete(it) }
        return WriteResult.Ok(Unit)
    }

    override suspend fun sell(dishId: Long, deviceId: String): WriteResult<DishDto> =
        repository.sell(dishId, deviceId).toWriteResult()

    override suspend fun unsell(dishId: Long, deviceId: String): WriteResult<DishDto> {
        repository.dishById(dishId) ?: return WriteResult.NotFound
        return repository.unsell(dishId, deviceId).toWriteResult()
    }

    override suspend fun devices(): List<DeviceDto> = deviceDao.getAll().map { it.toDto() }

    override suspend fun saveImage(bytes: ByteArray): String? = imageStore.importFromBytes(bytes)

    override suspend fun readImage(name: String): ImagePayload? {
        val bytes = imageStore.readBytes("${ImageStore.IMAGES_DIR}/$name") ?: return null
        val extension = name.substringAfterLast('.', "")
        val contentType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: "application/octet-stream"
        return ImagePayload(bytes, contentType)
    }

    override suspend fun currentVersion(): Long = repository.currentVersion()

    override suspend fun resetMenu() {
        repository.resetToSeedMenu()
    }

    /** 设备注册表写入：WS 连接建立时登记 / 刷新。 */
    suspend fun upsertDevice(id: String, name: String, role: String) {
        deviceDao.upsert(ConnectedDevice(id = id, name = name, role = role, lastSeen = now()))
    }

    /** 设备注册表写入：WS 断开时刷新 `lastSeen`。 */
    suspend fun touchDevice(id: String) {
        val existing = deviceDao.getById(id) ?: return
        deviceDao.upsert(existing.copy(lastSeen = now()))
    }

    private fun SellResult.toWriteResult(): WriteResult<DishDto> = when (this) {
        is SellResult.Success -> WriteResult.Ok(dish.toDto())
        is SellResult.Failure -> when (error) {
            SellError.NotFound -> WriteResult.NotFound
            else -> WriteResult.Conflict
        }
    }
}

private fun ConnectedDevice.toDto(): DeviceDto =
    DeviceDto(id = id, name = name, role = role, lastSeen = Iso8601.format(lastSeen))
