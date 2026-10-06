package com.tanpai.menu.ui.display

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tanpai.menu.R
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 二维码放大浮层的行为验收：展开可见、超时自动复原、点击任意处立即复原。
 *
 * 自动复原走真实的 5 秒计时，因此用例按真实时间等待（`waitUntil` 上限 8 秒）。
 */
@RunWith(AndroidJUnit4::class)
class WechatQrOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val qrFile: File = File.createTempFile("wechat_qr", ".png")

    private val hint: String =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.display_wechat_qr_hint)

    private val closeHint: String =
        InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.display_wechat_qr_close_hint)

    /** 用与展示页相同的条件渲染方式承载浮层：复原回调触发后浮层被移除。 */
    private fun setOverlayContent() {
        composeRule.setContent {
            var shown by remember { mutableStateOf(true) }
            if (shown) {
                WechatQrOverlay(qrFile = qrFile, onDismiss = { shown = false })
            }
        }
    }

    @Test
    fun showsHintAndCloseTip() {
        setOverlayContent()
        composeRule.onNodeWithText(hint).assertIsDisplayed()
        composeRule.onNodeWithText(closeHint).assertIsDisplayed()
    }

    @Test
    fun autoCollapsesAfterTimeout() {
        setOverlayContent()
        composeRule.onNodeWithText(hint).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithText(hint).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun tapCollapsesImmediately() {
        setOverlayContent()
        composeRule.onNodeWithText(closeHint).performClick()
        composeRule.onAllNodesWithText(hint).assertCountEquals(0)
    }
}
