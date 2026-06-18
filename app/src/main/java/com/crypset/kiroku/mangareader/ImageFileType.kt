package com.crypset.kiroku.mangareader

object ImageFileType {
    private val supportedImagePattern = Regex(
        ".*\\.(png|jpg|jpeg|webp|gif)$",
        RegexOption.IGNORE_CASE
    )

    fun isSupported(fileName: String): Boolean {
        return supportedImagePattern.matches(fileName)
    }
}

fun String.isSupportedImageFile(): Boolean {
    return ImageFileType.isSupported(this)
}
