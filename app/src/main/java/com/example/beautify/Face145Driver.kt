package com.example.beautify

object Face145Driver {
    private const val N = Face145Topology.VERTEX_COUNT
    private const val LM = Face145Topology.LANDMARK_COUNT
    private const val T = Face145Topology.EXTRAPOLATE_T

    @JvmStatic
    fun updateFromLandmarks106(landmarksUv: FloatArray, outXy: FloatArray): Boolean {
        if (landmarksUv.size < LM * 2 || outXy.size < N * 2) return false
        for (i in 0 until LM) {
            outXy[i * 2] = landmarksUv[i * 2]
            outXy[i * 2 + 1] = landmarksUv[i * 2 + 1]
        }
        return redriveDense(outXy)
    }

    private fun redriveDense(outXy: FloatArray): Boolean {
        val ox = 0.5f * (outXy[74 * 2] + outXy[0])
        val oy = 0.5f * (outXy[74 * 2 + 1] + outXy[1])
        val ax = 0.5f * (outXy[77 * 2] + outXy[32 * 2]) - ox
        val ay = 0.5f * (outXy[77 * 2 + 1] + outXy[32 * 2 + 1]) - oy
        val rx = -ay
        val ry = ax
        if (ax * ax + ay * ay < 1e-12f) return false

        val bp = Face145Topology.BASE_POINTS
        for (i in 0 until 11) {
            val bx = bp[i * 2]
            val by = bp[i * 2 + 1]
            outXy[(106 + i) * 2] = ox + bx * ax + by * rx
            outXy[(106 + i) * 2 + 1] = oy + bx * ay + by * ry
        }

        val mx = 0.5f * (outXy[43 * 2] + outXy[46 * 2])
        val my = 0.5f * (outXy[43 * 2 + 1] + outXy[46 * 2 + 1])
        for (k in 0 until 17) {
            val si = k * 2
            val sx = outXy[si * 2]
            val sy = outXy[si * 2 + 1]
            outXy[(117 + k) * 2] = sx + (mx - sx) * T
            outXy[(117 + k) * 2 + 1] = sy + (my - sy) * T
        }

        val p43x = outXy[43 * 2]
        val p43y = outXy[43 * 2 + 1]
        for (k in 0 until 11) {
            val sx = outXy[(106 + k) * 2]
            val sy = outXy[(106 + k) * 2 + 1]
            outXy[(134 + k) * 2] = sx + (p43x - sx) * T
            outXy[(134 + k) * 2 + 1] = sy + (p43y - sy) * T
        }
        return true
    }
}
