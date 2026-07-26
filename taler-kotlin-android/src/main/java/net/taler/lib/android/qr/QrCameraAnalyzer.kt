/*
 * This file is part of GNU Taler
 * (C) 2026 Taler Systems S.A.
 *
 * GNU Taler is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3, or (at your option) any later version.
 *
 * GNU Taler is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * GNU Taler; see the file COPYING.  If not, see <http://www.gnu.org/licenses/>
 */

package net.taler.lib.android.qr

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.BarcodeFormat
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.PlanarYUVLuminanceSource
import java.util.concurrent.Executor

@androidx.annotation.OptIn(ExperimentalGetImage::class)
class QrCameraAnalyzer(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onQrDetected: (String) -> Unit,
    private val onCameraReady: ((initialLinearZoom: Float) -> Unit)? = null,
) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var camera: Camera? = null

    private val qrReader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }

    fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build().also { useCase ->
                    useCase.setAnalyzer(mainExecutor) { proxy ->
                        val mediaImage = proxy.image
                        if (mediaImage != null) {
                            val nv21 = yuv420888ToNv21(mediaImage)
                            val width = mediaImage.width
                            val height = mediaImage.height
                            val rotation = proxy.imageInfo.rotationDegrees

                            val (rotatedNv21, rotatedWidth, rotatedHeight) = when (rotation) {
                                90 -> rotateNV21(nv21, width, height, 90)
                                180 -> rotateNV21(nv21, width, height, 180)
                                270 -> rotateNV21(nv21, width, height, 270)
                                else -> Triple(nv21, width, height)
                            }

                            val source = PlanarYUVLuminanceSource(
                                rotatedNv21, rotatedWidth, rotatedHeight, 0, 0, rotatedWidth, rotatedHeight, false,
                            )

                            val bitmap = BinaryBitmap(HybridBinarizer(source))
                            try {
                                val result = qrReader.decodeWithState(bitmap)
                                onQrDetected(result.text)
                            } catch (_: NotFoundException) {
                            } finally {
                                proxy.close()
                            }
                        } else {
                            proxy.close()
                        }
                    }
                }

            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
            camera?.cameraInfo?.zoomState?.value?.let { zoomState ->
                onCameraReady?.invoke(zoomState.linearZoom)
            }
        }, mainExecutor)
    }

    fun stopCamera() {
        try {
            ProcessCameraProvider.getInstance(context).get().unbindAll()
            camera = null
        } catch (_: Exception) {
        }
    }

    fun enableTorch(enabled: Boolean) {
        camera?.cameraControl?.enableTorch(enabled)
    }

    fun setLinearZoom(zoom: Float) {
        camera?.cameraControl?.setLinearZoom(zoom.coerceIn(0f, 1f))
    }

    fun focusAtPoint(x: Float, y: Float) {
        val cam = camera ?: return
        val factory = SurfaceOrientedMeteringPointFactory(
            previewView.width.toFloat(),
            previewView.height.toFloat(),
        )
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    private fun yuv420888ToNv21(image: Image): ByteArray {
        val yPlane = image.planes[0].buffer
        val uPlane = image.planes[1].buffer
        val vPlane = image.planes[2].buffer
        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()
        val nv21 = ByteArray(ySize + uSize + vSize)
        yPlane.get(nv21, 0, ySize)
        vPlane.get(nv21, ySize, vSize)
        uPlane.get(nv21, ySize + vSize, uSize)
        return nv21
    }

    private fun rotateNV21(data: ByteArray, width: Int, height: Int, rotation: Int): Triple<ByteArray, Int, Int> {
        if (rotation == 0) return Triple(data, width, height)

        val ySize = width * height
        val uvSize = ySize / 2
        val rotated = ByteArray(ySize + uvSize)

        when (rotation) {
            90 -> {
                var i = 0
                for (x in 0 until width) {
                    for (y in height - 1 downTo 0) {
                        rotated[i++] = data[y * width + x]
                    }
                }
                var uvIndex = ySize
                for (x in 0 until width step 2) {
                    for (y in height - 1 downTo 0 step 2) {
                        val uvPos = ySize + (y * width + x)
                        if (uvPos + 1 < data.size) {
                            rotated[uvIndex++] = data[uvPos]
                            rotated[uvIndex++] = data[uvPos + 1]
                        }
                    }
                }
                return Triple(rotated, height, width)
            }
            180 -> {
                var i = ySize - 1
                for (j in 0 until ySize) {
                    rotated[j] = data[i--]
                }
                i = data.size - 1
                for (j in ySize until data.size step 2) {
                    rotated[j] = data[i - 1]
                    rotated[j + 1] = data[i]
                    i -= 2
                }
                return Triple(rotated, width, height)
            }
            270 -> {
                var i = 0
                for (x in width - 1 downTo 0) {
                    for (y in 0 until height) {
                        rotated[i++] = data[y * width + x]
                    }
                }
                var uvIndex = ySize
                for (x in width - 1 downTo 0 step 2) {
                    for (y in 0 until height step 2) {
                        val uvPos = ySize + (y * width + x - 1)
                        if (uvPos + 1 < data.size) {
                            rotated[uvIndex++] = data[uvPos]
                            rotated[uvIndex++] = data[uvPos + 1]
                        }
                    }
                }
                return Triple(rotated, height, width)
            }
        }
        return Triple(data, width, height)
    }

    companion object {
        fun decodeQrFromBitmap(bitmap: Bitmap): String? {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            val source = RGBLuminanceSource(width, height, pixels)
            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            val reader = MultiFormatReader().apply {
                setHints(
                    mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.CHARACTER_SET to "UTF-8",
                    ),
                )
            }

            return try {
                reader.decodeWithState(binaryBitmap).text
            } catch (_: NotFoundException) {
                null
            }
        }
    }
}
