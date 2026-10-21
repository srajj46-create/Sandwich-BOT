package com.sandwichbot

/**
 * Frame-difference tracker. Screen me jo cheez move ho rahi hai (aata hua sandwich)
 * uska x-centroid track karta hai aur jab wo target x cross karne wala ho tab true return karta hai.
 */
class Tracker(private val w: Int, private val h: Int) {
    private var cur = IntArray(w * h)
    private var prev = IntArray(w * h)
    private var hasPrev = false

    private val y0 = (h * 0.18f).toInt()   // upar ka HUD chhod do
    private val y1 = (h * 0.80f).toInt()   // neeche ka ad banner chhod do
    private val area = (y1 - y0) * w

    private var tracking = false
    private var dir = 0
    private var cooldownUntil = 0L
    private var lastSeen = 0L
    private var startedAt = 0L

    private val ts = LongArray(6)
    private val xs = FloatArray(6)
    private var n = 0

    fun buffer(): IntArray = cur

    fun process(now: Long): Boolean {
        var tap = false
        if (hasPrev) {
            var cnt = 0
            var sx = 0L
            for (y in y0 until y1) {
                val row = y * w
                for (x in 0 until w) {
                    val d = cur[row + x] - prev[row + x]
                    if (d > 28 || d < -28) { cnt++; sx += x }
                }
            }
            val frac = cnt.toFloat() / area
            if (frac > 0.30f) {
                // camera scroll / screen change
                tracking = false
                cooldownUntil = now + 500
            } else if (cnt >= area * 0.004f) {
                tap = step(sx.toFloat() / cnt / w, now)
            } else if (tracking && now - lastSeen > 250) {
                tracking = false
            }
        }
        val t = cur; cur = prev; prev = t
        hasPrev = true
        return tap
    }

    private fun step(cx: Float, now: Long): Boolean {
        if (now < cooldownUntil) return false
        if (!tracking) {
            if (cx < 0.22f || cx > 0.78f) {
                tracking = true
                dir = if (cx < 0.5f) 1 else -1
                n = 0
                startedAt = now
            } else return false
        }
        lastSeen = now
        push(now, cx)
        if (n < 3) return false

        val v = slope()
        if (v * dir < 0.25f) {
            if (now - startedAt > 400) tracking = false
            return false
        }
        val target = Config.targetPm / 1000f
        val predicted = cx + v * (Config.leadMs / 1000f)
        val before = (cx - target) * dir < 0
        if (before && (predicted - target) * dir >= 0) {
            tracking = false
            cooldownUntil = now + 450
            return true
        }
        if (!before) tracking = false
        return false
    }

    private fun push(t: Long, x: Float) {
        if (n == ts.size) {
            for (i in 1 until n) { ts[i - 1] = ts[i]; xs[i - 1] = xs[i] }
            n--
        }
        ts[n] = t; xs[n] = x; n++
    }

    private fun slope(): Float {
        var st = 0f; var sx = 0f
        for (i in 0 until n) { st += (ts[i] - ts[0]) / 1000f; sx += xs[i] }
        val mt = st / n; val mx = sx / n
        var num = 0f; var den = 0f
        for (i in 0 until n) {
            val t = (ts[i] - ts[0]) / 1000f - mt
            num += t * (xs[i] - mx); den += t * t
        }
        return if (den < 1e-6f) 0f else num / den
    }
}
