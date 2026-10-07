package com.tanpai.menu.data

import com.tanpai.menu.data.db.Category
import com.tanpai.menu.data.db.Dish

/**
 * 主机写事务提交后的变更事件，供 WS 广播使用（`docs/design/0002` 第 6.2 节）。
 *
 * [version] 为本次提交后的全局版本号；事务回滚不发事件。
 * 事件只描述「发生了什么」，到线报文的映射由 `net/protocol` 负责。
 */
sealed interface MenuChange {

    val version: Long

    data class DishUpdated(override val version: Long, val dish: Dish) : MenuChange

    data class DishDeleted(override val version: Long, val dishId: Long) : MenuChange

    data class CategoryUpdated(override val version: Long, val category: Category) : MenuChange

    /** 删除分类会级联软删除其下菜品，[dishIds] 为受影响菜品，便于对端逐条清理。 */
    data class CategoryDeleted(
        override val version: Long,
        val categoryId: Long,
        val dishIds: List<Long> = emptyList()
    ) : MenuChange

    /** 整库替换（导入备份 / 首启种子）：对端应重新拉取 `/state`。 */
    data class FullReload(override val version: Long) : MenuChange

    /**
     * 展示设置变更（`docs/design/0023` 第 2.2 节）。
     *
     * [version] 沿用当前全局版本（展示设置与菜单数据无关，不 bump）；
     * 客户端不复用「版本前进」判据，而是比对设置内容。
     */
    data class DisplayUpdated(override val version: Long) : MenuChange
}