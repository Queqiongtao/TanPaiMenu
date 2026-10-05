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