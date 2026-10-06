package com.tanpai.menu.ui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tanpai.menu.R
import com.tanpai.menu.ui.theme.ChineseFontFamily
import java.io.File
import kotlinx.coroutines.delay

/** 放大浮层展开后自动复原的时长。 */
const val WECHAT_QR_AUTO_COLLAPSE_MILLIS = 5_000L

/** 放大二维码边长上限（屏足够宽时取该值）。 */
private val WechatQrMaxSide = 480.dp

/** 放大二维码边长占浮层可用宽度的比例。 */
private const val WechatQrWidthFraction = 0.6f

/** 放大卡片最大高度占浮层高度的比例，四周留出边距避免贴边。 */
private const val WechatQrCardHeightFraction = 0.92f

/** 放大卡片中除二维码外的固定占用（内边距 + 标题 + 间距 + 关闭提示）。 */
private val WechatQrChromeHeight = 160.dp

/** 放大卡片内边距。 */
private val WechatQrCardPadding = 24.dp

/**
 * 微信二维码放大浮层：全屏半透明遮罩 + 居中白底大码，供顾客在桌边扫码。
 *
 * 展开 [WECHAT_QR_AUTO_COLLAPSE_MILLIS] 毫秒后自动复原；期间点击任意位置（含卡片）立即复原。
 * 浮层由调用方按条件渲染，创建即开始计时、销毁即结束，重复展开不会叠加计时。
 */
@Composable
fun WechatQrOverlay(
    qrFile: File,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(Unit) {
        delay(WECHAT_QR_AUTO_COLLAPSE_MILLIS)
        onDismiss()
    }
    val interactionSource = remember { MutableInteractionSource() }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        val qrSide = minOf(
            maxWidth * WechatQrWidthFraction,
            maxHeight * WechatQrCardHeightFraction - WechatQrChromeHeight,
            WechatQrMaxSide
        )
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(WechatQrCardPadding)
                .width(qrSide),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.display_wechat_qr_hint),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 28.sp),
                fontFamily = ChineseFontFamily,
                color = MaterialTheme.colorScheme.primary
            )
            AsyncImage(
                model = qrFile,
                contentDescription = stringResource(R.string.display_wechat_qr_hint),
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentScale = ContentScale.Fit
            )
            Text(
                text = stringResource(R.string.display_wechat_qr_close_hint),
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = ChineseFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
