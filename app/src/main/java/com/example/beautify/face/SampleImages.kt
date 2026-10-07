package com.example.beautify.face

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

object SampleImages {

    fun loadSdkSample(context: Context): Bitmap? {
        return runCatching {
            context.assets.open("beauty_demo_before_720p.jpg").use { input ->
                BitmapFactory.decodeStream(input)?.copy(Bitmap.Config.ARGB_8888, true)
            }
        }.getOrNull()
    }
}
