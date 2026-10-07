package com.tanpai.menu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tanpai.menu.data.Role
import com.tanpai.menu.ui.RoleSelectScreen
import com.tanpai.menu.ui.TanPaiMenuApp
import com.tanpai.menu.ui.client.ClientApp

class MainActivity : ComponentActivity() {

    /** 当前角色；仅用于重新获得焦点时恢复系统栏形态。 */
    private var role: Role? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        val container = app.container
        role = container.settingsStore.role
        applySystemBars(role)

        setContent {
            var currentRole by mutableStateOf(role)
            val switchRole: (Role) -> Unit = { chosen ->
                app.applyRole(chosen)
                role = chosen
                currentRole = chosen
                applySystemBars(chosen)
            }

            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentRole) {
                        null -> RoleSelectScreen(onChosen = switchRole)

                        Role.HOST -> TanPaiMenuApp(
                            repository = container.menuRepository,
                            settings = container.settingsStore,
                            pinManager = container.pinManager,
                            imageStore = container.imageStore,
                            backupManager = container.backupManager,
                            hostStatus = container.hostStatus,
                            onSwitchToClient = { switchRole(Role.CLIENT) }
                        )

                        Role.CLIENT -> ClientApp(
                            session = container.clientSession,
                            pairingClient = container.pairingClient,
                            settings = container.settingsStore,
                            onSwitchToHost = { switchRole(Role.HOST) }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySystemBars(role)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars(role)
    }

    /**
     * 按角色设置系统栏：主机形态沉浸式（隐藏状态栏与导航栏），其余形态显示常规系统栏
     * （`docs/design/0018` 第 2.2 节）。
     */
    private fun applySystemBars(role: Role?) {
        val immersive = role == Role.HOST
        WindowCompat.setDecorFitsSystemWindows(window, !immersive)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (immersive) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}