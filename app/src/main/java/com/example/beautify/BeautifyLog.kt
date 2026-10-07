package com.example.beautify

import android.util.Log

internal object BeautifyLog {
    private const val minLevel: Int = Log.WARN

    @JvmStatic
    fun w(tag: String, msg: String) {
        if (Log.WARN >= minLevel) Log.w(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
    }

    @JvmStatic
    fun e(tag: String, msg: String, tr: Throwable?) {
        if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
    }
}
