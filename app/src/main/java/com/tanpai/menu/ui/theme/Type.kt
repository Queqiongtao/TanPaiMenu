package com.tanpai.menu.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.tanpai.menu.R

/**
 * 内置中文字体：Noto Sans SC Bold（SIL OFL 1.1）。
 *
 * 用于店名、分类名与菜名；文件本身即粗体，使用处不再叠加 [androidx.compose.ui.text.font.FontWeight.Bold]，
 * 避免二次加粗发虚。许可文本见 `docs/third-party/NotoSansSC-OFL.txt`。
 */
val ChineseFontFamily: FontFamily = FontFamily(Font(R.font.noto_sans_sc_bold))

/**
 * 展示页「店名」专用字体：霞鹜文楷 Medium 的 GB2312 常用字子集（SIL OFL 1.1）。
 *
 * 只用于展示页店名招牌，菜名、分类名与其余界面仍用 [ChineseFontFamily]，避免小字号可读性下降。
 * 子集由 `scripts/fetch-store-name-font.ps1` 生成，生僻字运行期回退系统字体；
 * 许可文本见 `docs/third-party/LXGWWenKai-OFL.txt`，设计说明见 `docs/design/0008-店名艺术化与顶栏布局.md`。
 */
val StoreNameFontFamily: FontFamily = FontFamily(Font(R.font.store_name_wenkai))