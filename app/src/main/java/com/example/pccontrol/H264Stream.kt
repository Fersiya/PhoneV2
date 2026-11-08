package com.example.pccontrol

import android.graphics.Bitmap
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import kotlin.coroutines.coroutineContext

/**
 * H.264-трансляция (бета). Хост шлёт пакеты: >I длина, B флаги (1 = ключевой), 4×float область экрана, данные Annex-B.
 * Кадры декодируются MediaCodec и превращаются в Bitmap — дальше всё как у обычного потока (зум, нажатия, счётчик кадров).
 * Возвращает текст ошибки, если поток не удалось запустить или он оборвался; "" — обычная остановка.
 */
suspend fun runH264Stream(
    host: String,
    token: String,
    fps: Int,
    width: Int,
    kbps: Int,
    view: FloatArray,
    onFrame: (ImageBitmap, Int, Long, FloatArray) -> Unit
): String = withContext(Dispatchers.IO) {
    val viewQ = "&x0=${view[0]}&y0=${view[1]}&x1=${view[2]}&y1=${view[3]}"
    val conn = URL(buildPcUrl(host, "/h264?fps=$fps&w=$width&kbps=$kbps$viewQ")).openConnection() as HttpURLConnection
    var codec: MediaCodec? = null
    try {
        conn.requestMethod = "GET"
        conn.connectTimeout = 3000
        conn.readTimeout = 12000
        val safeToken = if (token.all { it.code in 0x20..0x7E }) token else ""
        conn.setRequestProperty("X-Token", safeToken)
        if (conn.responseCode != 200) {
            val body = try { conn.errorStream?.bufferedReader()?.readText().orEmpty() } catch (_: Exception) { "" }
            return@withContext "H264: HTTP ${conn.responseCode} $body".trim()
        }
        val w = conn.getHeaderField("X-Width")?.toIntOrNull() ?: width
        val h = conn.getHeaderField("X-Height")?.toIntOrNull() ?: (width * 9 / 16)
        val encName = conn.getHeaderField("X-Encoder").orEmpty()
        AppLog.add("H.264: ${w}x$h, кодировщик на ПК: $encName")

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            if (Build.VERSION.SDK_INT >= 30) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        val dec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec = dec
        dec.configure(format, null, null, 0)
        dec.start()

        // Два Bitmap по очереди, чтобы не создавать новые на каждый кадр
        val bmps = arrayOf(Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888), Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888))
        var bmpIdx = 0
        val pixels = IntArray(w * h)
        val sentAt = java.util.concurrent.ConcurrentHashMap<Long, Pair<Long, FloatArray>>() // pts -> (время отправки в декодер, область экрана)

        val scope = this
        val drain = launch(Dispatchers.IO) {
            val info = MediaCodec.BufferInfo()
            var frames = 0
            var lastFpsTime = System.currentTimeMillis()
            var calcFps = fps
            var decNs = 0L
            var statFrames = 0
            var lastLogMs = System.currentTimeMillis()
            while (isActive) {
                val idx = try { dec.dequeueOutputBuffer(info, 20_000) } catch (_: Exception) { break }
                if (idx < 0) continue
                try {
                    if (info.size > 0) {
                        val img = dec.getOutputImage(idx)
                        if (img != null) {
                            val t0 = System.nanoTime()
                            yuvToArgb(img, pixels, w, h)
                            val bmp = bmps[bmpIdx]
                            bmpIdx = 1 - bmpIdx
                            bmp.setPixels(pixels, 0, w, 0, 0, w, h)
                            img.close()
                            decNs += System.nanoTime() - t0
                            statFrames++
                            val meta = sentAt.remove(info.presentationTimeUs)
                            val nowMs = System.currentTimeMillis()
                            frames++
                            if (nowMs - lastFpsTime >= 1000) { calcFps = frames; frames = 0; lastFpsTime = nowMs }
                            val ping = if (meta != null) nowMs - meta.first else 0L
                            if (nowMs - lastLogMs >= 10000 && statFrames > 0) {
                                AppLog.add(L("H.264: получено $calcFps к/с, преобразование ${decNs / statFrames / 1_000_000} мс на кадр"))
                                decNs = 0; statFrames = 0; lastLogMs = nowMs
                            }
                            val fv = meta?.second ?: floatArrayOf(0f, 0f, 1f, 1f)
                            val shown = bmp.asImageBitmap()
                            launch(Dispatchers.Main.immediate) { onFrame(shown, calcFps, ping, fv) }
                        }
                    }
                } finally {
                    try { dec.releaseOutputBuffer(idx, false) } catch (_: Exception) {}
                }
                if (sentAt.size > 200) sentAt.clear()
            }
        }

        val input = DataInputStream(conn.inputStream.buffered(128 * 1024))
        val head = ByteArray(21)
        var data = ByteArray(256 * 1024)
        var gotKey = false
        while (coroutineContext.isActive) {
            input.readFully(head)
            val bb = ByteBuffer.wrap(head) // big-endian по умолчанию
            val len = bb.int
            val flags = bb.get().toInt()
            val fv = floatArrayOf(bb.float, bb.float, bb.float, bb.float)
            if (len <= 0 || len > 8 * 1024 * 1024) return@withContext "H264: некорректный пакет ($len)"
            if (len > data.size) data = ByteArray(len + 65536)
            input.readFully(data, 0, len)
            val key = flags and 1 != 0
            if (!gotKey) {
                if (!key) continue // начинаем только с ключевого кадра
                gotKey = true
            }
            val inIdx = dec.dequeueInputBuffer(50_000)
            if (inIdx < 0) continue // декодер не успевает — пропускаем кадр (следующий ключевой восстановит картинку)
            val buf = dec.getInputBuffer(inIdx) ?: continue
            buf.clear()
            buf.put(data, 0, len)
            val pts = System.nanoTime() / 1000
            sentAt[pts] = System.currentTimeMillis() to fv
            dec.queueInputBuffer(inIdx, 0, len, pts, 0)
        }
        drain.cancel()
        ""
    } catch (e: java.io.EOFException) {
        "H264: поток закрыт"
    } catch (e: Exception) {
        "H264: ${e.localizedMessage ?: e.javaClass.simpleName}"
    } finally {
        try { codec?.stop() } catch (_: Exception) {}
        try { codec?.release() } catch (_: Exception) {}
        try { conn.disconnect() } catch (_: Exception) {}
    }
}

/** YUV 4:2:0 (любая раскладка плоскостей) → ARGB_8888, целочисленная математика BT.601. */
internal fun yuvToArgb(img: Image, out: IntArray, w: Int, h: Int) {
    val planes = img.planes
    val yBuf = planes[0].buffer
    val uBuf = planes[1].buffer
    val vBuf = planes[2].buffer
    val yRow = planes[0].rowStride
    val uRow = planes[1].rowStride
    val uPix = planes[1].pixelStride
    val vRow = planes[2].rowStride
    val vPix = planes[2].pixelStride
    val ys = ByteArray(yBuf.remaining()).also { yBuf.get(it) }
    val us = ByteArray(uBuf.remaining()).also { uBuf.get(it) }
    val vs = ByteArray(vBuf.remaining()).also { vBuf.get(it) }
    var o = 0
    for (j in 0 until h) {
        val yBase = j * yRow
        val uBase = (j shr 1) * uRow
        val vBase = (j shr 1) * vRow
        for (i in 0 until w) {
            val y = (ys[yBase + i].toInt() and 0xFF) - 16
            val ui = (i shr 1) * uPix
            val vi = (i shr 1) * vPix
            val u = (us[uBase + ui].toInt() and 0xFF) - 128
            val v = (vs[vBase + vi].toInt() and 0xFF) - 128
            val c = 298 * (if (y < 0) 0 else y)
            var r = (c + 409 * v + 128) shr 8
            var g = (c - 100 * u - 208 * v + 128) shr 8
            var b = (c + 516 * u + 128) shr 8
            if (r < 0) r = 0 else if (r > 255) r = 255
            if (g < 0) g = 0 else if (g > 255) g = 255
            if (b < 0) b = 0 else if (b > 255) b = 255
            out[o++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
