package com.tanpai.menu.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 管理密码的修改与重置（`docs/design/0017` 第 5 节）：
 * 清除后不再是「已设置」状态，重新设置后旧密码失效、新密码可用。
 */
@RunWith(AndroidJUnit4::class)
class PinManagerTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = PinManager(SettingsStore(context))

    @Before
    fun setUp() {
        manager.clearPin()
    }

    @After
    fun tearDown() {
        manager.clearPin()
    }

    @Test
    fun clearPinRemovesProtection() {
        manager.setPin("1234")
        assertTrue(manager.isPinSet())

        manager.clearPin()

        assertFalse(manager.isPinSet())
        assertFalse(manager.verify("1234"))
    }

    @Test
    fun setPinReplacesOldPassword() {
        manager.setPin("1234")
        manager.setPin("5678")

        assertFalse("旧密码仍然可用", manager.verify("1234"))
        assertTrue(manager.verify("5678"))
    }
}