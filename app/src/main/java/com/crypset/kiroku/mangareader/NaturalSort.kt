package com.crypset.kiroku.mangareader

import androidx.documentfile.provider.DocumentFile

object NaturalSort {
    private val partPattern = Regex("(\\d+)|(\\D+)")

    val documentFileComparator: Comparator<DocumentFile> = Comparator { first, second ->
        compare(first.name.orEmpty(), second.name.orEmpty())
    }

    fun compare(first: String, second: String): Int {
        return try {
            compareParts(first.parts(), second.parts())
        } catch (_: Exception) {
            first.compareTo(second, ignoreCase = true)
        }
    }

    private fun compareParts(firstParts: List<String>, secondParts: List<String>): Int {
        for (index in 0 until minOf(firstParts.size, secondParts.size)) {
            val comparison = comparePart(firstParts[index], secondParts[index])
            if (comparison != 0) return comparison
        }

        return firstParts.size.compareTo(secondParts.size)
    }

    private fun comparePart(first: String, second: String): Int {
        val firstIsNumber = first.all { it.isDigit() }
        val secondIsNumber = second.all { it.isDigit() }

        return if (firstIsNumber && secondIsNumber) {
            (first.toLongOrNull() ?: 0L).compareTo(second.toLongOrNull() ?: 0L)
        } else {
            first.compareTo(second, ignoreCase = true)
        }
    }

    private fun String.parts(): List<String> {
        return partPattern.findAll(this).map { it.value }.toList()
    }
}
