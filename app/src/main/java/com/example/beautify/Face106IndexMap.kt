package com.example.beautify

object Face106IndexMap {
    private const val N = 106
    private const val PUPIL_L = 67
    private const val PUPIL_R = 68

    @JvmField
    val HL_INDEX_FOR_TT_SLOT: IntArray = intArrayOf(
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23,
        24, 25, 26, 27, 28, 29, 30, 31, 32, 42, 43, 44, 45, 46, 37, 36, 35, 34, 33, 101, 103,
        102, 100, 92, 93, 94, 95, 96, 59, 60, 62, 63, 64, 66, 55, 54, 52, 51, 58, 56, 50, 49,
        48, 47, 38, 39, 40, 41, 61, 65, 68, 53, 57, 67, 89, 99, 90, 98, 91, 97, 71, 72, 73, 74,
        75, 76, 77, 78, 79, 80, 81, 82, 105, 88, 87, 86, 104, 85, 84, 83, 70, 69
    )

    private val HL_MIRROR_INDEX: IntArray = buildHlMirrorIndex()

    init {
        require(HL_INDEX_FOR_TT_SLOT.size == N)
        require(HL_INDEX_FOR_TT_SLOT.toSet().size == N) { "remap not bijective" }
    }

    @JvmStatic
    fun hyperLandmarkToTtFace(hlXy: FloatArray): FloatArray {
        if (hlXy.size < N * 2) return hlXy
        val mirrored = hlXy[PUPIL_L * 2] > hlXy[PUPIL_R * 2]
        val src = if (mirrored) hlXy else mirrorHyperLandmark(hlXy)
        val out = FloatArray(N * 2)
        val map = HL_INDEX_FOR_TT_SLOT
        for (tt in 0 until N) {
            val hl = map[tt]
            out[tt * 2] = src[hl * 2]
            out[tt * 2 + 1] = src[hl * 2 + 1]
        }
        return out
    }

    private fun mirrorHyperLandmark(hlXy: FloatArray): FloatArray {
        val out = FloatArray(N * 2)
        for (i in 0 until N) {
            val j = HL_MIRROR_INDEX[i]
            out[i * 2] = hlXy[j * 2]
            out[i * 2 + 1] = hlXy[j * 2 + 1]
        }
        return out
    }

    private fun buildHlMirrorIndex(): IntArray {
        val m = IntArray(N) { it }
        for (i in 0..32) m[i] = 32 - i
        for (i in 0..8) {
            m[33 + i] = 50 - i
            m[42 + i] = 41 - i
        }
        for (i in 0..7) {
            m[51 + i] = 59 + i
            m[59 + i] = 51 + i
        }
        m[67] = 68
        m[68] = 67
        m[69] = 70
        m[70] = 69
        for (i in 0..11) m[71 + i] = 82 - i
        for (i in 0..5) m[83 + i] = 88 - i
        for (i in 0..14) m[89 + i] = 103 - i
        m[104] = 105
        m[105] = 104
        return m
    }
}
