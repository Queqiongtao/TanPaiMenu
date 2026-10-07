package com.tanpai.menu.ui.client

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 手机端菜品列表底部留白单测（`docs/design/0023` 第 2.1 节）。
 *
 * 真机上管理模式的行内「沽清 / 删除」右对齐、正好被右下角的「新增菜品」悬浮按钮盖住，
 * 因此在「管理模式 + 已选分类」时必须预留足够的底部空间。
 */
class ClientMenuScreenTest {

    @Test
    fun reservesClearanceWhenAdminModeAndCategorySelected() {
        val clearance = dishListBottomClearance(adminMode = true, hasSelectedCategory = true)

        assertEquals(88.dp, clearance)
    }

    @Test
    fun noClearanceWhenFabIsHidden() {
        // 只读浏览或没有分类时不显示悬浮按钮，不需要留白。
        assertEquals(0.dp, dishListBottomClearance(adminMode = false, hasSelectedCategory = true))
        assertEquals(0.dp, dishListBottomClearance(adminMode = true, hasSelectedCategory = false))
        assertEquals(0.dp, dishListBottomClearance(adminMode = false, hasSelectedCategory = false))
    }
}
