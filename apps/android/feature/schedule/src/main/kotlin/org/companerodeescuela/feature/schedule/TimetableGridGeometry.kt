package org.companerodeescuela.feature.schedule

/**
 * Finds the eight day boundaries in a seven-day timetable printed as a grid.
 * A vertical rule is much more reliable than the left edge of an OCR token:
 * PDF titles are left-aligned while teacher names are right-aligned.
 *
 * The callback makes the geometry independently unit-testable without Android Bitmap.
 * Returns Monday..Sunday [start, end) x coordinates, or null for a non-grid page.
 */
internal fun detectWeeklyPdfGrid(
    width: Int,
    height: Int,
    isDarkPixel: (Int, Int) -> Boolean,
): List<Pair<Float, Float>>? {
    if (width < 160 || height < 160) return null
    val firstY = (height * 0.21f).toInt()
    val lastY = (height * 0.88f).toInt()
    val sampleCount = 52
    val sampleYs = (0 until sampleCount).map { i ->
        firstY + (lastY - firstY) * i / (sampleCount - 1)
    }

    // Border lines are continuous across most rows. Ordinary printed text
    // never occupies 75% of these distant sample points at a fixed x.
    val inkColumns = (1 until width - 1).filter { x ->
        sampleYs.count { y -> isDarkPixel(x, y) } >= sampleCount * 3 / 4
    }
    if (inkColumns.isEmpty()) return null

    val runs = mutableListOf<MutableList<Int>>()
    inkColumns.forEach { x ->
        if (runs.isEmpty() || x - runs.last().last() > 5) runs.add(mutableListOf())
        runs.last().add(x)
    }
    val rules = runs.map { group -> group.average().toFloat() }
    // Left outer border, time rail separator, six internal day dividers,
    // and the final Sunday edge (nine vertical rules in total).
    if (rules.size != 9) return null

    val dayWidths = rules.drop(1).zipWithNext { a, b -> b - a }
    if (dayWidths.size != 7) return null
    val typical = dayWidths.sorted()[3]
    if (typical < 25f || dayWidths.any { it !in typical * 0.80f..typical * 1.20f }) return null
    val timeRailWidth = rules[1] - rules[0]
    if (timeRailWidth <= 0f || timeRailWidth > typical * 1.40f) return null

    return (1..7).map { index -> rules[index] to rules[index + 1] }
}
