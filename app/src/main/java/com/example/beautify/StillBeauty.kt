package com.example.beautify

import android.content.Context
import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object StillBeauty {
    private const val TAG = "StillBeauty"
    private const val SAVE_SIDE_DEFAULT = 3840
    private const val SAVE_SIDE_LO = 1280
    private const val BLACK_MEAN_LUMA = 6f

    @Volatile
    private var cachedGlMaxTex = 0

    @Volatile
    var faceDetector: BeautyFaceDetector? = null

    fun process(context: Context, src: Bitmap, params: BeautyPanelParams): Bitmap {
        if (!params.shouldProcess()) return src
        if (src.width < 8 || src.height < 8 || src.isRecycled) return src
        val detector = faceDetector ?: return src
        val face = runCatching { detector.detect(context, src) }.getOrNull()
        if (face == null || !face.hasFace) return src
        val landmarksUv = face.landmarksUv ?: return src
        if (landmarksUv.size < Face145Topology.LANDMARK_COUNT * 2) return src
        val srcLuma = sampleMeanLuma(src)
        return runCatching {
            val out = processGl(context, src, params.smooth, landmarksUv, deviceWorkSide())
            val outLuma = sampleMeanLuma(out)
            if (out !== src && srcLuma >= BLACK_MEAN_LUMA && outLuma < BLACK_MEAN_LUMA) {
                BeautifyLog.e(TAG, "BLACK_OUTPUT — keep original")
                if (!out.isRecycled) out.recycle()
                return@runCatching src
            }
            out
        }.onFailure { t ->
            BeautifyLog.e(TAG, "still beauty failed", t)
        }.getOrNull() ?: src
    }

    private fun deviceWorkSide(): Int {
        val gl = cachedGlMaxTex.takeIf { it > 0 } ?: 0
        val cap = if (gl > 0) min(SAVE_SIDE_DEFAULT, gl) else SAVE_SIDE_DEFAULT
        return cap.coerceIn(SAVE_SIDE_LO, SAVE_SIDE_DEFAULT)
    }

    private fun sampleMeanLuma(bmp: Bitmap): Float {
        if (bmp.isRecycled || bmp.width < 1 || bmp.height < 1) return 0f
        val stepX = (bmp.width / 32).coerceAtLeast(1)
        val stepY = (bmp.height / 32).coerceAtLeast(1)
        var sum = 0L
        var n = 0
        var y = 0
        while (y < bmp.height) {
            var x = 0
            while (x < bmp.width) {
                val p = bmp.getPixel(x, y)
                val r = (p shr 16) and 0xff
                val g = (p shr 8) and 0xff
                val b = p and 0xff
                sum += (r * 3 + g * 6 + b) / 10
                n++
                x += stepX
            }
            y += stepY
        }
        return if (n == 0) 0f else sum.toFloat() / n
    }

    private fun processGl(
        context: Context,
        src: Bitmap,
        smooth: Float,
        landmarksUv: FloatArray,
        maxSide: Int,
    ): Bitmap {
        val work = scaleForBeauty(src, maxSide)
        val scaled = work !== src
        try {
            return processGlSized(context, work, smooth, landmarksUv)
        } finally {
            if (scaled && !work.isRecycled) work.recycle()
        }
    }

    private fun scaleForBeauty(src: Bitmap, maxSide: Int): Bitmap {
        val cap = maxSide.coerceAtLeast(8)
        val side = max(src.width, src.height)
        if (side <= cap) return src
        val scale = cap.toFloat() / side
        val w = (src.width * scale).roundToInt().coerceAtLeast(8)
        val h = (src.height * scale).roundToInt().coerceAtLeast(8)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun processGlSized(
        context: Context,
        src: Bitmap,
        smooth: Float,
        landmarksUv: FloatArray,
    ): Bitmap {
        val w = src.width
        val h = src.height
        var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
        var eglCtx: EGLContext = EGL14.EGL_NO_CONTEXT
        var surface: EGLSurface = EGL14.EGL_NO_SURFACE
        val pipeline = SmoothV10Pipeline(context)
        val tex = IntArray(1)
        val fbo = IntArray(1)
        val outTex = IntArray(1)
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) error("no egl display")
            val vers = IntArray(2)
            if (!EGL14.eglInitialize(display, vers, 0, vers, 1)) error("eglInit")
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val num = IntArray(1)
            if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, num, 0) || num[0] <= 0) {
                error("eglChooseConfig")
            }
            val config = configs[0] ?: error("eglConfig")
            val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            eglCtx = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
            val pbufferAttribs = intArrayOf(EGL14.EGL_WIDTH, 8, EGL14.EGL_HEIGHT, 8, EGL14.EGL_NONE)
            surface = EGL14.eglCreatePbufferSurface(display, config, pbufferAttribs, 0)
            if (eglCtx == EGL14.EGL_NO_CONTEXT || surface == EGL14.EGL_NO_SURFACE) error("eglCreate")
            if (!EGL14.eglMakeCurrent(display, surface, surface, eglCtx)) error("eglMakeCurrent")

            val maxTex = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTex, 0)
            if (maxTex[0] > 0) cachedGlMaxTex = maxTex[0]
            if (w > maxTex[0] || h > maxTex[0]) return src

            if (!pipeline.init() || !pipeline.isReady) {
                BeautifyLog.e(TAG, "beauty program not ready — keep original")
                return src
            }
            pipeline.setInputNeedsYFlip(true)
            pipeline.setLandmarks106Uv(landmarksUv)

            GLES20.glGenTextures(1, tex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, src, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

            GLES20.glGenTextures(1, outTex, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, outTex[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glGenFramebuffers(1, fbo, 0)
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
            GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, outTex[0], 0
            )
            if (GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
                != GLES20.GL_FRAMEBUFFER_COMPLETE
            ) {
                error("fbo incomplete")
            }
            GLES20.glViewport(0, 0, w, h)
            pipeline.process(tex[0], w, h, true, smooth)

            val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo[0])
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            buf.rewind()
            val pixels = IntArray(w * h)
            for (y in 0 until h) {
                val dstY = h - 1 - y
                for (x in 0 until w) {
                    val r = buf.get().toInt() and 0xff
                    val g = buf.get().toInt() and 0xff
                    val b = buf.get().toInt() and 0xff
                    val a = buf.get().toInt() and 0xff
                    pixels[dstY * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        } finally {
            pipeline.release()
            if (fbo[0] != 0) GLES20.glDeleteFramebuffers(1, fbo, 0)
            if (tex[0] != 0) GLES20.glDeleteTextures(1, tex, 0)
            if (outTex[0] != 0) GLES20.glDeleteTextures(1, outTex, 0)
            releaseEgl(display, surface, eglCtx)
        }
    }

    private fun releaseEgl(display: EGLDisplay, surface: EGLSurface, ctx: EGLContext) {
        if (display == EGL14.EGL_NO_DISPLAY) return
        runCatching {
            EGL14.eglMakeCurrent(
                display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
        }
        if (surface != EGL14.EGL_NO_SURFACE) runCatching { EGL14.eglDestroySurface(display, surface) }
        if (ctx != EGL14.EGL_NO_CONTEXT) runCatching { EGL14.eglDestroyContext(display, ctx) }
        runCatching { EGL14.eglReleaseThread() }
    }
}
