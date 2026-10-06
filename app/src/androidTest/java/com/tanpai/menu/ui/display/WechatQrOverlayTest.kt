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

    /**
     * 用与展示页相同的条件渲染方式承载浮层：复原回调触发后浮层被移除。
     *
     * [onDismissed] 在浮层复原时回调，用于断言「复原」这一行为——浮层移除后内容为空，
     * 再去查询语义树会拿不到 Compose 层级。
     */
    private fun setOverlayContent(model: Any = qrFile, onDismissed: () -> Unit = {}) {
        composeRule.setContent {
            var shown by remember { mutableStateOf(true) }
            if (shown) {
                WechatQrOverlay(
                    qrModel = model,
                    onDismiss = {
                        shown = false
                        onDismissed()
                    }
                )
            }
        }
    }

    @Test
    fun rendersBuiltInDefaultImage() {
        setOverlayContent(R.drawable.wechat_qr_default)
        composeRule.onNodeWithText(hint).assertIsDisplayed()
        composeRule.onNodeWithText(closeHint).assertIsDisplayed()
    }

    @Test
    fun showsHintAndCloseTip() {
        setOverlayContent()
        composeRule.onNodeWithText(hint).assertIsDisplayed()
        composeRule.onNodeWithText(closeHint).assertIsDisplayed()
    }

    @Test
    fun autoCollapsesAfterTimeout() {
        var dismissCount = 0
        setOverlayContent(onDismissed = { dismissCount++ })
        composeRule.onNodeWithText(hint).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 8_000) { dismissCount == 1 }
    }

    @Test
    fun tapCollapsesImmediately() {
        setOverlayContent()
        composeRule.onNodeWithText(closeHint).performClick()
        composeRule.onAllNodesWithText(hint).assertCountEquals(0)
    }
}
