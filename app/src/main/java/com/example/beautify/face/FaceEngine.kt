package com.example.beautify.face

import android.content.Context
import android.util.Log
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.CustomParameter
import com.insightface.sdk.inspireface.base.Session

object FaceEngine {
    private const val TAG = "FaceEngine"
    private const val MODEL = InspireFace.PIKACHU
    private const val TRACK_PREVIEW_SIZE = 320
    private const val DETECT_INPUT_PX = 320

    @Volatile
    private var launched = false

    @Synchronized
    fun ensureLaunched(context: Context): Boolean {
        if (launched) return true
        launched = InspireFace.GlobalLaunch(context.applicationContext, MODEL) == true
        Log.i(TAG, "GlobalLaunch($MODEL) -> $launched")
        return launched
    }

    @Synchronized
    fun createTrackingSession(): Session? {
        val parameter: CustomParameter = InspireFace.CreateCustomParameter()
        val session = InspireFace.CreateSession(
            parameter,
            InspireFace.DETECT_MODE_LIGHT_TRACK,
             1,
            DETECT_INPUT_PX,
             -1
        ) ?: return null
        InspireFace.SetTrackPreviewSize(session, TRACK_PREVIEW_SIZE)
        InspireFace.SetFaceDetectThreshold(session, 0.5f)
        InspireFace.SetFilterMinimumFacePixelSize(session, 0)
        return session
    }

    @Synchronized
    fun releaseSession(session: Session?) {
        if (session != null) {
            InspireFace.ReleaseSession(session)
        }
    }

    @Synchronized
    fun terminate() {
        if (!launched) return
        InspireFace.GlobalTerminate()
        launched = false
    }

    fun modelName(): String = MODEL
}
