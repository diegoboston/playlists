package com.playlists.app.util

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Four page corners in order TL, TR, BR, BL. Coordinates are either pixels or 0–1. */
data class PageQuad(
    val topLeft: PagePoint,
    val topRight: PagePoint,
    val bottomRight: PagePoint,
    val bottomLeft: PagePoint,
) {
    fun points(): List<PagePoint> = listOf(topLeft, topRight, bottomRight, bottomLeft)

    fun toPixels(width: Int, height: Int): PageQuad = PageQuad(
        topLeft.toPixels(width, height),
        topRight.toPixels(width, height),
        bottomRight.toPixels(width, height),
        bottomLeft.toPixels(width, height),
    )

    fun toNormalized(width: Int, height: Int): PageQuad = PageQuad(
        topLeft.toNormalized(width, height),
        topRight.toNormalized(width, height),
        bottomRight.toNormalized(width, height),
        bottomLeft.toNormalized(width, height),
    )

    fun clamped(): PageQuad = PageQuad(
        topLeft.clamped(),
        topRight.clamped(),
        bottomRight.clamped(),
        bottomLeft.clamped(),
    )

    fun withPoint(index: Int, point: PagePoint): PageQuad = when (index) {
        0 -> copy(topLeft = point)
        1 -> copy(topRight = point)
        2 -> copy(bottomRight = point)
        3 -> copy(bottomLeft = point)
        else -> this
    }

    companion object {
        const val DEFAULT_INSET = 0.08f

        fun normalizedInset(inset: Float = DEFAULT_INSET): PageQuad = PageQuad(
            PagePoint(inset, inset),
            PagePoint(1f - inset, inset),
            PagePoint(1f - inset, 1f - inset),
            PagePoint(inset, 1f - inset),
        )
    }
}

data class PagePoint(val x: Float, val y: Float) {
    fun clamped(): PagePoint = PagePoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))

    fun toPixels(width: Int, height: Int): PagePoint =
        PagePoint(x * width, y * height)

    fun toNormalized(width: Int, height: Int): PagePoint =
        PagePoint(x / width.coerceAtLeast(1), y / height.coerceAtLeast(1))
}

data class FitRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

object PageGeometry {
    const val MAX_EDGE = 3000
    private const val MIN_NORMALIZED_AREA = 0.02f
    private const val MAX_NORMALIZED_AREA = 0.99f

    fun contentFit(imageWidth: Int, imageHeight: Int, viewWidth: Int, viewHeight: Int): FitRect {
        val scale = min(
            viewWidth.toFloat() / imageWidth.coerceAtLeast(1),
            viewHeight.toFloat() / imageHeight.coerceAtLeast(1),
        )
        val width = imageWidth * scale
        val height = imageHeight * scale
        return FitRect(
            left = (viewWidth - width) / 2f,
            top = (viewHeight - height) / 2f,
            width = width,
            height = height,
        )
    }

    fun toViewX(normX: Float, fit: FitRect): Float = fit.left + normX * fit.width

    fun toViewY(normY: Float, fit: FitRect): Float = fit.top + normY * fit.height

    fun fromViewX(viewX: Float, fit: FitRect): Float =
        if (fit.width <= 0f) 0f else ((viewX - fit.left) / fit.width).coerceIn(0f, 1f)

    fun fromViewY(viewY: Float, fit: FitRect): Float =
        if (fit.height <= 0f) 0f else ((viewY - fit.top) / fit.height).coerceIn(0f, 1f)

    fun isUsableNormalized(quad: PageQuad): Boolean {
        if (quad.points().any { it.x !in 0f..1f || it.y !in 0f..1f }) return false
        if (!isConvex(quad)) return false
        val area = abs(shoelace(quad))
        return area in MIN_NORMALIZED_AREA..MAX_NORMALIZED_AREA
    }

    fun destSize(pixelQuad: PageQuad, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val width = (
            dist(pixelQuad.topLeft, pixelQuad.topRight) +
                dist(pixelQuad.bottomLeft, pixelQuad.bottomRight)
            ) / 2f
        val height = (
            dist(pixelQuad.topLeft, pixelQuad.bottomLeft) +
                dist(pixelQuad.topRight, pixelQuad.bottomRight)
            ) / 2f
        val longest = maxOf(width, height, 1f)
        val scale = if (longest > maxEdge) maxEdge / longest else 1f
        return (width * scale).roundToInt().coerceAtLeast(1) to
            (height * scale).roundToInt().coerceAtLeast(1)
    }

    fun isConvex(quad: PageQuad): Boolean {
        val pts = quad.points()
        var sign = 0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            val c = pts[(i + 2) % 4]
            val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (abs(cross) < 1e-4f) continue
            val next = if (cross > 0f) 1 else -1
            if (sign != 0 && next != sign) return false
            sign = next
        }
        return sign != 0
    }

    fun shoelace(quad: PageQuad): Float {
        val pts = quad.points()
        var sum = 0f
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % 4]
            sum += a.x * b.y - b.x * a.y
        }
        return sum / 2f
    }

    private fun dist(a: PagePoint, b: PagePoint): Float = hypot(b.x - a.x, b.y - a.y)
}
