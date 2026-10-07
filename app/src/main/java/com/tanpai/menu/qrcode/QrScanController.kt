package com.tanpai.menu.qrcode

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.TextureView
import com.tanpai.menu.logging.TpLog
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * 扫码用的相机预览与取帧（`docs/design/0022` 第 2.2 节）。
 *
 * 用 Camera2 直接拿 `YUV_420_888` 帧，只取 Y 平面交 [QrDecoder] 解码，避免引入 CameraX。
 * 线程模型：相机会话回调在私有 [cameraThread] 上，帧解码在 [analyzerThread] 上互不阻塞；
 * 回调 [onError] 一律切回主线程，调用方可以直接更新 Compose 状态。
 *
 * 生命周期归调用方所有：[attach] 绑定 `TextureView`，[start] 打开相机，[stop] 释放全部资源；
 * 三者都做了幂等与顺序容错，允许「先 start 后 attach」或反复 stop。
 */
class QrScanController(
    private val context: Context,
    /**
     * 解码命中回调（主线程）；返回 true 表示已消费该内容（停止再回调），
     * 返回 false 表示配对失败、可以继续扫描下一个二维码。
     */
    private val onDecoded: (String) -> Boolean,
    private val onError: (String) -> Unit
) {

    private val mainHandler = Handler(Looper.getMainLooper())

    private val cameraThread = HandlerThread("tp-qr-camera").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)
    private val analyzerThread = HandlerThread("tp-qr-decode").apply { start() }
    private val analyzerHandler = Handler(analyzerThread.looper)

    private val decoding = AtomicBoolean(false)
    private val locking = AtomicBoolean(false)

    /** 命中闸门：一次只处理一个扫码结果，避免同一二维码被连续回调多次。 */
    private val delivered = AtomicBoolean(false)

    private var textureView: TextureView? = null
    private var cameraDevice: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    private var previewSize: Size = Size(0, 0)
    private var lastFocusAt = 0L
    private var stopped = false

    /** 绑定预览控件；[start] 之前或之后调用都可以。 */
    fun attach(view: TextureView) {
        textureView = view
        view.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) =
                openCameraIfReady()

            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) =
                applyPreviewTransform()

            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                closeCamera()
                return true
            }

            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
        if (view.isAvailable) openCameraIfReady()
    }

    /** 打开相机（异步）；设备无后置摄像头或已被占用时走 [onError]。 */
    fun start() {
        stopped = false
        openCameraIfReady()
    }

    /** 释放相机与线程；可重复调用。 */
    fun stop() {
        stopped = true
        closeCamera()
        cameraThread.quitSafely()
        analyzerThread.quitSafely()
    }

    private fun openCameraIfReady() {
        val view = textureView ?: return
        if (stopped || cameraDevice != null) return
        if (!view.isAvailable || view.width <= 0 || view.height <= 0) return
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: run {
            onError("本机没有可用的相机")
            return
        }
        try {
            val cameraId = chooseCameraId(manager) ?: run {
                onError("本机没有可用的后置摄像头")
                return
            }
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val yuvSizes = map?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)?.toList().orEmpty()
            val previewSizes = map?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
            val candidates: List<Size> = yuvSizes.ifEmpty { previewSizes }
            if (candidates.isEmpty()) {
                onError("相机不支持扫码预览")
                return
            }
            previewSize = pickSize(candidates)
            TpLog.i(TAG, "扫码相机已选择：id=$cameraId 预览=${previewSize.width}x${previewSize.height}")
            imageReader = ImageReader.newInstance(
                previewSize.width,
                previewSize.height,
                android.graphics.ImageFormat.YUV_420_888,
                IMAGE_BUFFER_COUNT
            ).apply { setOnImageAvailableListener({ drainFrames() }, analyzerHandler) }
            manager.openCamera(cameraId, openCallback, cameraHandler)
        } catch (e: CameraAccessException) {
            onError("无法打开相机：${e.message ?: "被系统或其它应用占用"}")
        } catch (e: SecurityException) {
            onError("没有相机权限，请在系统设置中允许后重试")
        } catch (e: IllegalArgumentException) {
            onError("相机参数不合法，无法启动扫码")
        }
    }

    private val openCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(device: CameraDevice) {
            cameraDevice = device
            createSession(device)
        }

        override fun onDisconnected(device: CameraDevice) {
            device.close()
            cameraDevice = null
            onError("相机连接中断，请重新打开扫码")
        }

        override fun onError(device: CameraDevice, error: Int) {
            device.close()
            cameraDevice = null
            onError("相机启动失败（错误码 $error）")
        }
    }

    /**
     * 建立预览 + 取帧会话。
     *
     * 使用 `List<Surface>` 形式的重载：`SessionConfiguration` 变体要 API 28，而本工程 `minSdk = 24`。
     */
    @Suppress("DEPRECATION")
    private fun createSession(device: CameraDevice) {
        val view = textureView ?: return
        val texture = view.surfaceTexture ?: return
        val reader = imageReader ?: return
        texture.setDefaultBufferSize(previewSize.width, previewSize.height)
        val previewSurface = Surface(texture)
        try {
            device.createCaptureSession(
                listOf(previewSurface, reader.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(created: CameraCaptureSession) {
                        session = created
                        startPreview(device, created, previewSurface)
                        applyPreviewTransform()
                        cameraHandler.removeCallbacks(focusRunnable)
                        cameraHandler.postDelayed(focusRunnable, FOCUS_INTERVAL_MILLIS)
                    }

                    override fun onConfigureFailed(created: CameraCaptureSession) {
                        onError("相机预览启动失败，请重试")
                    }
                },
                cameraHandler
            )
        } catch (e: CameraAccessException) {
            onError("相机预览启动失败：${e.message ?: "未知原因"}")
        }
    }

    private fun startPreview(
        device: CameraDevice,
        created: CameraCaptureSession,
        previewSurface: Surface
    ) {
        try {
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(previewSurface)
                imageReader?.let { addTarget(it.surface) }
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }
            created.setRepeatingRequest(request.build(), null, cameraHandler)
        } catch (e: CameraAccessException) {
            onError("相机预览启动失败：${e.message ?: "未知原因"}")
        } catch (e: IllegalStateException) {
            onError("相机预览启动失败，请重试")
        }
    }

    /** 取最新一帧，拷贝出紧凑灰度数据后立即归还缓冲；解码期间后续帧直接丢弃。 */
    private fun drainFrames() {
        val reader = imageReader ?: return
        if (!decoding.compareAndSet(false, true)) return
        var image: android.media.Image? = null
        try {
            image = try {
                reader.acquireLatestImage()
            } catch (e: IllegalStateException) {
                null
            } ?: return
            if (image.format != android.graphics.ImageFormat.YUV_420_888) return
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val width = image.width
            val height = image.height
            val data = copyLuminance(buffer, width, height, plane.rowStride)
            val text = QrDecoder.decode(data, width, height) ?: return
            if (!delivered.compareAndSet(false, true)) return
            mainHandler.post { if (!onDecoded(text)) delivered.set(false) }
        } finally {
            image?.let { runCatching { it.close() } }
            decoding.set(false)
        }
    }

    /** 把 Y 平面拷成紧凑灰度数组；行间有填充字节时逐行拷贝。 */
    private fun copyLuminance(
        buffer: java.nio.ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int
    ): ByteArray {
        val data = ByteArray(width * height)
        runCatching {
            if (rowStride == width) {
                buffer.get(data, 0, data.size)
            } else {
                for (row in 0 until height) {
                    val offset = row * rowStride
                    if (offset + width > buffer.limit()) break
                    buffer.position(offset)
                    buffer.get(data, row * width, width)
                }
            }
        }
        return data
    }

    /** 周期性补一次自动对焦，缓解近距离扫屏时连续对焦不收敛。 */
    private val focusRunnable = object : Runnable {
        override fun run() {
            val active = session
            val device = cameraDevice
            val view = textureView
            if (active != null && device != null && view != null) {
                val now = System.currentTimeMillis()
                if (now - lastFocusAt >= FOCUS_INTERVAL_MILLIS && locking.compareAndSet(false, true)) {
                    lastFocusAt = now
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            view.surfaceTexture?.let { addTarget(Surface(it)) }
                            imageReader?.let { addTarget(it.surface) }
                            set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                        }
                        active.capture(request.build(), focusCallback, cameraHandler)
                    } catch (e: CameraAccessException) {
                        locking.set(false)
                    } catch (e: IllegalStateException) {
                        locking.set(false)
                    }
                }
            }
            cameraHandler.postDelayed(this, FOCUS_INTERVAL_MILLIS)
        }
    }

    private val focusCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: android.hardware.camera2.CaptureRequest,
            result: android.hardware.camera2.TotalCaptureResult
        ) {
            locking.set(false)
        }

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: android.hardware.camera2.CaptureRequest,
            failure: android.hardware.camera2.CaptureFailure
        ) {
            locking.set(false)
        }
    }

    /**
     * 预览放入 `TextureView`：以窗口形状为准的等比 center-crop（多出的边裁掉，绝不拉伸）。
     *
     * 计算用「视图尺寸 → 纹理尺寸」的比值：`scale = max(视宽/缓冲宽, 视高/缓冲高)`，
     * 它保证可见纹理区 `视图尺寸 / scale` 不会超出缓冲区；随后在缓冲坐标里居中。
     * 写成倒数（`max(缓冲/视图)`）在窗口与缓冲区同比例时看不出问题，一旦比例不同就会出现
     * 黑边或被裁空（真机实测在 16:9 取景框 + 4:3 相机流时整块变黑）。
     */
    private fun applyPreviewTransform() {
        val view = textureView ?: return
        val bufferWidth = previewSize.width.toFloat()
        val bufferHeight = previewSize.height.toFloat()
        if (bufferWidth <= 0f || bufferHeight <= 0f) return
        val viewWidth = view.width.toFloat()
        val viewHeight = view.height.toFloat()
        if (viewWidth <= 0f || viewHeight <= 0f) return

        val scale = max(viewWidth / bufferWidth, viewHeight / bufferHeight)
        val visibleWidth = viewWidth / scale
        val visibleHeight = viewHeight / scale
        val dx = (bufferWidth - visibleWidth) / 2f
        val dy = (bufferHeight - visibleHeight) / 2f

        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        view.setTransform(matrix)
    }

    /** 关闭会话与相机；可在相机线程或其它线程调用。 */
    private fun closeCamera() {
        cameraHandler.removeCallbacks(focusRunnable)
        locking.set(false)
        session?.let { active ->
            runCatching { active.stopRepeating() }
            runCatching { active.close() }
        }
        session = null
        cameraDevice?.let { device -> runCatching { device.close() } }
        cameraDevice = null
        imageReader?.let { reader ->
            runCatching { reader.setOnImageAvailableListener(null, null) }
            runCatching { reader.close() }
        }
        imageReader = null
    }

    private fun chooseCameraId(manager: CameraManager): String? {
        val ids = runCatching { manager.cameraIdList }.getOrNull().orEmpty()
        if (ids.isEmpty()) return null
        val facing = ids.firstOrNull { id ->
            runCatching { manager.getCameraCharacteristics(id) }
                .getOrNull()
                ?.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
        return facing ?: ids.first()
    }

    /**
     * 选预览尺寸：在 [MIN_PREVIEW_PIXELS]–[MAX_PREVIEW_PIXELS] 之间取最接近 [TARGET_PREVIEW_PIXELS]
     * 的一档；都不在区间内时取最小的一档兜底。
     *
     * 刻意不按取景窗口大小选：窗口尺寸由预览宽高比决定，按窗口选会形成循环依赖，
     * 真机实测曾因此选到 352x288 这种过低分辨率。
     */
    private fun pickSize(candidates: List<Size>): Size {
        val byArea = candidates.sortedBy { it.width.toLong() * it.height }
        val usable = byArea.filter {
            val area = it.width.toLong() * it.height
            area in MIN_PREVIEW_PIXELS.toLong()..MAX_PREVIEW_PIXELS.toLong()
        }
        val pool = usable.ifEmpty { byArea }
        return pool.minByOrNull { abs(it.width.toLong() * it.height - TARGET_PREVIEW_PIXELS) }
            ?: pool.first()
    }

    private companion object {
        const val TAG = "qr"
        const val IMAGE_BUFFER_COUNT = 2
        const val MIN_PREVIEW_PIXELS = 640 * 480
        const val TARGET_PREVIEW_PIXELS = 1280 * 960
        const val MAX_PREVIEW_PIXELS = 1920 * 1080
        const val FOCUS_INTERVAL_MILLIS = 1_500L
    }
}
