package com.example.beautify.face

import android.content.Context
import android.graphics.Bitmap
import com.example.beautify.BeautyFaceDetector
import com.example.beautify.BeautyFaceHint

object BeautyFaceAdapter : BeautyFaceDetector {
    override fun detect(context: Context, bitmap: Bitmap): BeautyFaceHint? {
        val r = LandmarkDetector.detect(context, bitmap)
        if (!r.hasFace) {
            return BeautyFaceHint(hasFace = false, normBox = null)
        }
        return BeautyFaceHint(
            hasFace = true,
            normBox = r.normBox,
            landmarksUv = r.landmarksUv,
        )
    }
}
