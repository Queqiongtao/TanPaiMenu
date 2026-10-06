package com.tanpai.menu.ui.display

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanpai.menu.R
import com.tanpai.menu.data.db.Dish
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 价格胶囊的边界验收：常规价、含角分、极长价格、无单位与售完态都能正常渲染。
 *
 * 只断言「渲染出来且可见」——排版宽度由真机截图把关（见 `docs/design/0012-展示页价格强调.md`）。
 */
@RunWith(AndroidJUnit4::class)
class DishCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val soldOutText: String =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.display_sold_out)

    private fun dish(priceCents: Int, unit: String = "份", soldOut: Boolean = false) = Dish(
        categoryId = 1L,
        name = "原味鲜肉",
        priceCents = priceCents,
        unit = unit,
        soldOut = soldOut,
        updatedAt = 0L
    )

    private fun setCard(dish: Dish) {
        composeRule.setContent {
            DishCard(dish = dish, modifier = Modifier.fillMaxWidth())
        }
    }

    @Test
    fun showsWholeYuanPriceWithUnit() {
        setCard(dish(priceCents = 1000))
        composeRule.onNodeWithText("¥10 / 份").assertIsDisplayed()
    }

    @Test
    fun showsJiaoFenPrice() {
        setCard(dish(priceCents = 1050))
        composeRule.onNodeWithText("¥10.50 / 份").assertIsDisplayed()
    }

    @Test
    fun showsBlankUnitWithoutSeparator() {
        setCard(dish(priceCents = 1200, unit = ""))
        composeRule.onNodeWithText("¥12").assertIsDisplayed()
    }

    @Test
    fun showsExtremePrice() {
        setCard(dish(priceCents = 999999))
        composeRule.onNodeWithText("¥9999.99 / 份").assertIsDisplayed()
    }

    @Test
    fun showsPriceAndSoldOutBadge() {
        setCard(dish(priceCents = 1000, soldOut = true))
        composeRule.onNodeWithText("¥10 / 份").assertIsDisplayed()
        composeRule.onNodeWithText(soldOutText).assertIsDisplayed()
    }
}
