package com.tanpai.menu.client

import com.tanpai.menu.net.protocol.CategoryDto
import com.tanpai.menu.net.protocol.DishDto
import com.tanpai.menu.net.protocol.EventType
import com.tanpai.menu.net.protocol.IdPayload
import com.tanpai.menu.net.protocol.StateDto
import com.tanpai.menu.net.protocol.WireEvent
import com.tanpai.menu.net.protocol.payloadAs

/** 事件应用结果。 */
enum class EventApplyResult {
    /** 已应用，或按版本规则丢弃，本地状态仍然一致。 */
    APPLIED,

    /** 报文无法解释，需要断开重连并重新拉快照。 */
    NEED_RESYNC
}

/**
 * 客户端内存镜像（`docs/design/0019` 第 2.3 节）。
 *
 * 只保存契约 DTO，不落库；由 `ClientSession` 的单协程串行访问，因此内部无需加锁。
 * 规则：`snapshot` 全量替换；增量事件要求 `version` 严格大于本地版本，否则丢弃；
 * `device.*` 与未知类型忽略（不影响菜单数据）。
 */
class ClientMirror {

    private val categories = LinkedHashMap<Long, CategoryDto>()
    private val dishes = LinkedHashMap<Long, DishDto>()

    /** 本地版本号，取最近一次应用成功的快照或事件版本。 */
    var version: Long = 0L
        private set

    fun applySnapshot(state: StateDto) {
        categories.clear()
        dishes.clear()
        state.categories.forEach { categories[it.id] = it }
        state.dishes.forEach { dishes[it.id] = it }
        version = state.version
    }

    fun applyEvent(event: WireEvent): EventApplyResult {
        if (event.type == EventType.DEVICE_JOINED || event.type == EventType.DEVICE_LEFT) {
            return EventApplyResult.APPLIED
        }
        if (event.type == EventType.SNAPSHOT) {
            val state = event.payloadAs<StateDto>() ?: return EventApplyResult.NEED_RESYNC
            applySnapshot(state)
            return EventApplyResult.APPLIED
        }
        if (event.version <= version) return EventApplyResult.APPLIED
        return when (event.type) {
            EventType.DISH_UPDATED -> {
                val dish = event.payloadAs<DishDto>() ?: return EventApplyResult.NEED_RESYNC
                dishes[dish.id] = dish
                version = event.version
                EventApplyResult.APPLIED
            }

            EventType.DISH_DELETED -> {
                val payload = event.payloadAs<IdPayload>() ?: return EventApplyResult.NEED_RESYNC
                dishes.remove(payload.id)
                version = event.version
                EventApplyResult.APPLIED
            }

            EventType.CATEGORY_UPDATED -> {
                val category = event.payloadAs<CategoryDto>() ?: return EventApplyResult.NEED_RESYNC
                categories[category.id] = category
                version = event.version
                EventApplyResult.APPLIED
            }

            EventType.CATEGORY_DELETED -> {
                val payload = event.payloadAs<IdPayload>() ?: return EventApplyResult.NEED_RESYNC
                categories.remove(payload.id)
                dishes.filterValues { it.categoryId == payload.id }.keys.forEach { dishes.remove(it) }
                version = event.version
                EventApplyResult.APPLIED
            }

            else -> EventApplyResult.APPLIED
        }
    }

    fun categories(): List<CategoryDto> =
        categories.values.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    fun dishes(): List<DishDto> = dishes.values.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    fun dishesOf(categoryId: Long): List<DishDto> = dishes().filter { it.categoryId == categoryId }

    fun clear() {
        categories.clear()
        dishes.clear()
        version = 0L
    }
}