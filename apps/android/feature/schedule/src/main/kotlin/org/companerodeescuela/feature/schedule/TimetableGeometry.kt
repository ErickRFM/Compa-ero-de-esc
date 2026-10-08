package org.companerodeescuela.feature.schedule

/** List-style day headings must fall back to ordinary OCR text instead of table reconstruction. */
internal fun timetableHeadersShareRow(centers: List<Pair<Float, Float>>, rowTolerance: Float): Boolean =
    centers.size >= 4 && rowTolerance > 0 &&
        centers.maxOf { it.second } - centers.minOf { it.second } <= rowTolerance &&
        centers.map { it.first }.sorted().zipWithNext().all { (a, b) -> b - a > 10f }
