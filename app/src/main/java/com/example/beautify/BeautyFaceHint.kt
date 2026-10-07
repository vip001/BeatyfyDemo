package com.example.beautify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF

data class BeautyFaceHint(
    val hasFace: Boolean,

    val normBox: RectF?,

    val landmarksUv: FloatArray? = null,
)

fun interface BeautyFaceDetector {
    fun detect(context: Context, bitmap: Bitmap): BeautyFaceHint?
}
