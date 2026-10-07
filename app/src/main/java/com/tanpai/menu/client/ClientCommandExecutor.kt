package com.tanpai.menu.client

import com.tanpai.menu.net.HostRestClient

/**
 * 写命令执行器（`docs/design/0020` 第 2.2 节）。
 *
 * 只做「命令 → 主机 REST 调用」，不含重试、等待广播与界面状态；
 * 主机地址 / token / 设备号通过闭包读取，配对信息变化后无需重建。
 */
class ClientCommandExecutor(
    private val rest: HostRestClient,
    private val hostAddress: () -> String,
    private val token: () -> String,
    private val deviceId: () -> String
) {

    /** 执行一条写命令；失败原样抛出 [com.tanpai.menu.net.HostApiException] 或 IO 异常。 */
    suspend fun execute(command: ClientCommand) {
        val address = hostAddress()
        val auth = token()
        when (command) {
            is ClientCommand.CreateCategory ->
                rest.createCategory(address, auth, command.toRequest())

            is ClientCommand.UpdateCategory ->
                rest.patchCategory(address, auth, command.id, command.toRequest())

            is ClientCommand.DeleteCategory -> rest.deleteCategory(address, auth, command.id)

            is ClientCommand.CreateDish -> rest.createDish(address, auth, command.toRequest())

            is ClientCommand.UpdateDish ->
                rest.patchDish(address, auth, command.id, command.toRequest())

            is ClientCommand.DeleteDish -> rest.deleteDish(address, auth, command.id)

            is ClientCommand.Sell -> rest.sell(address, auth, command.id, deviceId())

            is ClientCommand.Unsell -> rest.unsell(address, auth, command.id, deviceId())

            is ClientCommand.SetSoldOut ->
                rest.patchDish(address, auth, command.id, command.toRequest())

            ClientCommand.ResetMenu -> rest.resetMenu(address, auth)
        }
    }
}