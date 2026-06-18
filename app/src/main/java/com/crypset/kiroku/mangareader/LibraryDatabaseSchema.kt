package com.crypset.kiroku.mangareader

import android.database.sqlite.SQLiteDatabase

object LibraryDatabaseSchema {
    const val DATABASE_NAME = "library.db"
    const val DATABASE_VERSION = 1

    const val TABLE_METADATA = "metadata"
    const val TABLE_MANGA = "manga"
    const val TABLE_CHAPTERS = "chapters"
    const val TABLE_PAGES = "pages"

    const val KEY_ROOT_URI = "root_uri"

    const val COLUMN_KEY = "key"
    const val COLUMN_VALUE = "value"
    const val COLUMN_URI = "uri"
    const val COLUMN_NAME = "name"
    const val COLUMN_PAGE_URI = "uri"
    const val COLUMN_CHAPTER_URI = "chapter_uri"
    const val COLUMN_SORT_ORDER = "sort_order"
    const val COLUMN_MANGA_URI = "manga_uri"

    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_METADATA (
                $COLUMN_KEY TEXT PRIMARY KEY,
                $COLUMN_VALUE TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_MANGA (
                $COLUMN_URI TEXT PRIMARY KEY,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_SORT_ORDER INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_CHAPTERS (
                $COLUMN_URI TEXT PRIMARY KEY,
                $COLUMN_MANGA_URI TEXT NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_SORT_ORDER INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_MANGA_URI) REFERENCES $TABLE_MANGA($COLUMN_URI) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_PAGES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CHAPTER_URI TEXT NOT NULL,
                $COLUMN_PAGE_URI TEXT NOT NULL,
                $COLUMN_SORT_ORDER INTEGER NOT NULL,
                FOREIGN KEY($COLUMN_CHAPTER_URI) REFERENCES $TABLE_CHAPTERS($COLUMN_URI) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX index_chapters_manga_uri ON $TABLE_CHAPTERS($COLUMN_MANGA_URI)")
        db.execSQL("CREATE INDEX index_pages_chapter_uri ON $TABLE_PAGES($COLUMN_CHAPTER_URI)")
    }

    fun recreate(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_PAGES")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CHAPTERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_MANGA")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_METADATA")
        create(db)
    }

    fun clearLibraryTables(db: SQLiteDatabase) {
        db.delete(TABLE_PAGES, null, null)
        db.delete(TABLE_CHAPTERS, null, null)
        db.delete(TABLE_MANGA, null, null)
    }
}
