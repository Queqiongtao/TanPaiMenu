package com.tanpai.menu.data

/**
 * 设备角色（`docs/design/0018` 第 2.1 节）。
 *
 * 同一个 APK 两种形态：平板当主机（展示页 + 局域网服务），手机当客户端（随身改菜单）。
 */
enum class Role(val key: String) {
    HOST("host"),
    CLIENT("client");

    companion object {
        /** 解析偏好里的角色键；未知或缺失返回 null，表示尚未选择。 */
        fun fromKey(key: String?): Role? = entries.firstOrNull { it.key == key }
    }
}