package com.example.beautify.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.MultipleFaceData
import com.insightface.sdk.inspireface.base.Point2f
import com.insightface.sdk.inspireface.base.Session
import kotlin.math.max

object LandmarkDetector {
    private const val TAG = "LandmarkDetector"
    const val LANDMARK_COUNT = 106

    data class Result(
        val hasFace: Boolean,

        val normBox: RectF?,

        val landmarksPx: FloatArray? = null,

        val landmarksUv: FloatArray? = null,
        val pointCount: Int = 0,
    )

    fun detect(context: Context, bitmap: Bitmap): Result {
        if (!FaceEngine.ensureLaunched(context)) {
            Log.w(TAG, "FaceEngine launch failed")
            return Result(hasFace = false, normBox = null)
        }
        var session: Session? = null
        try {
            session = FaceEngine.createTrackingSession()
                ?: return Result(hasFace = false, normBox = null)
            val stream = InspireFace.CreateImageStreamFromBitmap(
                bitmap,
                InspireFace.CAMERA_ROTATION_0
            ) ?: return Result(hasFace = false, normBox = null)
            try {
                val faces = InspireFace.ExecuteFaceTrack(session, stream)
                return parseFaces(faces, bitmap.width, bitmap.height)
            } finally {
                InspireFace.ReleaseImageStream(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "detect failed", e)
            return Result(hasFace = false, normBox = null)
        } finally {
            FaceEngine.releaseSession(session)
        }
    }

    private fun parseFaces(faces: MultipleFaceData?, imageW: Int, imageH: Int): Result {
        if (faces == null || faces.detectedNum <= 0 || faces.rects.isNullOrEmpty()) {
            return Result(hasFace = false, normBox = null)
        }
        val rect = faces.rects!![0] ?: return Result(hasFace = false, normBox = null)
        val w = max(imageW, 1).toFloat()
        val h = max(imageH, 1).toFloat()
        val norm = RectF(
            (rect.x.toFloat() / w).coerceIn(0f, 1f),
            (rect.y.toFloat() / h).coerceIn(0f, 1f),
            ((rect.x + rect.width).toFloat() / w).coerceIn(0f, 1f),
            ((rect.y + rect.height).toFloat() / h).coerceIn(0f, 1f)
        )
        if (norm.width() <= 0f || norm.height() <= 0f) {
            return Result(hasFace = false, normBox = null)
        }
        val token = faces.tokens?.getOrNull(0)
            ?: return Result(hasFace = true, normBox = norm)
        val points = runCatching {
            InspireFace.GetFaceDenseLandmarkFromFaceToken(token)
        }.onFailure {
            Log.w(TAG, "GetFaceDenseLandmarkFromFaceToken failed", it)
        }.getOrNull()
        val px = toInterleaved(points)
        val uv = px?.let { toUv(it, w, h) }
        val count = (px?.size ?: 0) / 2
        Log.i(TAG, "face detected landmarks=$count box=${norm.width()}x${norm.height()}")
        return Result(
            hasFace = true,
            normBox = norm,
            landmarksPx = px,
            landmarksUv = uv,
            pointCount = count
        )
    }

    private fun toInterleaved(landmarks: Array<Point2f>?): FloatArray? {
        if (landmarks == null || landmarks.size < LANDMARK_COUNT) return null
        val out = FloatArray(LANDMARK_COUNT * 2)
        for (i in 0 until LANDMARK_COUNT) {
            val p = landmarks[i] ?: return null
            out[i * 2] = p.x
            out[i * 2 + 1] = p.y
        }
        return out
    }

    private fun toUv(px: FloatArray, imageW: Float, imageH: Float): FloatArray {
        val uv = FloatArray(px.size)
        for (i in 0 until LANDMARK_COUNT) {
            uv[i * 2] = px[i * 2] / imageW
            uv[i * 2 + 1] = px[i * 2 + 1] / imageH
        }
        return uv
    }
}
