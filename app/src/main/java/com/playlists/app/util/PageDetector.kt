package com.playlists.app.util

/**
 * Finds a page quadrilateral from a grayscale image by taking extrema of bright (paper) pixels.
 * No generative editing — corners only.
 */
object PageDetector {
    private const val MIN_PAPER_FRACTION = 0.18f
    private const val MAX_PAPER_FRACTION = 0.92f

    fun detectNormalized(luma: ByteArray, width: Int, height: Int): PageQuad? {
        if (width < 8 || height < 8 || luma.size != width * height) return null
        val blurred = boxBlur(luma, width, height)
        val threshold = otsuThreshold(blurred)
        var paperCount = 0
        var minSum = Float.POSITIVE_INFINITY
        var maxSum = Float.NEGATIVE_INFINITY
        var minDiff = Float.POSITIVE_INFINITY
        var maxDiff = Float.NEGATIVE_INFINITY
        var tl = PagePoint(0f, 0f)
        var br = PagePoint(0f, 0f)
        var tr = PagePoint(0f, 0f)
        var bl = PagePoint(0f, 0f)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val value = blurred[row + x].toInt() and 0xFF
                if (value <= threshold) continue
                paperCount++
                val xf = x.toFloat()
                val yf = y.toFloat()
                val sum = xf + yf
                val diff = xf - yf
                if (sum < minSum) {
                    minSum = sum
                    tl = PagePoint(xf, yf)
                }
                if (sum > maxSum) {
                    maxSum = sum
                    br = PagePoint(xf, yf)
                }
                if (diff > maxDiff) {
                    maxDiff = diff
                    tr = PagePoint(xf, yf)
                }
                if (diff < minDiff) {
                    minDiff = diff
                    bl = PagePoint(xf, yf)
                }
            }
        }
        val fraction = paperCount.toFloat() / luma.size
        if (fraction < MIN_PAPER_FRACTION || fraction > MAX_PAPER_FRACTION) return null
        val pixel = PageQuad(tl, tr, br, bl)
        val normalized = pixel.toNormalized(width, height).clamped()
        return normalized.takeIf { PageGeometry.isUsableNormalized(it) }
    }

    internal fun otsuThreshold(luma: ByteArray): Int {
        val hist = IntArray(256)
        for (b in luma) hist[b.toInt() and 0xFF]++
        val total = luma.size
        var sum = 0L
        for (i in 0..255) sum += i.toLong() * hist[i]
        var sumB = 0L
        var wB = 0
        var maxVar = 0.0
        var threshold = 128
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB / wB.toDouble()
            val mF = (sum - sumB) / wF.toDouble()
            val variance = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (variance > maxVar) {
                maxVar = variance
                threshold = t
            }
        }
        return threshold
    }

    private fun boxBlur(src: ByteArray, width: Int, height: Int): ByteArray {
        val tmp = ByteArray(src.size)
        val out = ByteArray(src.size)
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                var sum = 0
                var n = 0
                for (dx in -1..1) {
                    val xx = x + dx
                    if (xx !in 0 until width) continue
                    sum += src[row + xx].toInt() and 0xFF
                    n++
                }
                tmp[row + x] = (sum / n).toByte()
            }
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var n = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy !in 0 until height) continue
                    sum += tmp[yy * width + x].toInt() and 0xFF
                    n++
                }
                out[y * width + x] = (sum / n).toByte()
            }
        }
        return out
    }
}
