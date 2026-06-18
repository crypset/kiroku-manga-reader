package com.crypset.kiroku.mangareader

import android.content.Context

class ReaderSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(
        READER_SETTINGS_PREFS,
        Context.MODE_PRIVATE
    )

    var pageContainerWidthPercent: Int
        get() = preferences
            .getInt(KEY_PAGE_CONTAINER_WIDTH_PERCENT, DEFAULT_PAGE_CONTAINER_WIDTH_PERCENT)
            .coerceIn(MIN_PAGE_CONTAINER_WIDTH_PERCENT, MAX_PAGE_CONTAINER_WIDTH_PERCENT)
        set(value) {
            preferences.edit()
                .putInt(
                    KEY_PAGE_CONTAINER_WIDTH_PERCENT,
                    value.coerceIn(MIN_PAGE_CONTAINER_WIDTH_PERCENT, MAX_PAGE_CONTAINER_WIDTH_PERCENT)
                )
                .apply()
        }

    companion object {
        const val MIN_PAGE_CONTAINER_WIDTH_PERCENT = 50
        const val MAX_PAGE_CONTAINER_WIDTH_PERCENT = 200
        const val DEFAULT_PAGE_CONTAINER_WIDTH_PERCENT = 100

        private const val READER_SETTINGS_PREFS = "reader_settings"
        private const val KEY_PAGE_CONTAINER_WIDTH_PERCENT = "page_container_width_percent"
    }
}
