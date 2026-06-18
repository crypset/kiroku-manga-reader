package com.crypset.kiroku.mangareader

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_CHAPTER_URI
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_KEY
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_MANGA_URI
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_NAME
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_PAGE_URI
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_SORT_ORDER
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_URI
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.COLUMN_VALUE
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.DATABASE_NAME
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.DATABASE_VERSION
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.KEY_ROOT_URI
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.TABLE_CHAPTERS
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.TABLE_MANGA
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.TABLE_METADATA
import com.crypset.kiroku.mangareader.LibraryDatabaseSchema.TABLE_PAGES

class LibraryStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        LibraryDatabaseSchema.create(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        LibraryDatabaseSchema.recreate(db)
    }

    fun getRootUri(): String? {
        readableDatabase.query(
            TABLE_METADATA,
            arrayOf(COLUMN_VALUE),
            "$COLUMN_KEY = ?",
            arrayOf(KEY_ROOT_URI),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(COLUMN_VALUE) else null
        }
    }

    fun getManga(): List<MangaItem> {
        readableDatabase.query(
            TABLE_MANGA,
            arrayOf(COLUMN_URI, COLUMN_NAME),
            null,
            null,
            null,
            null,
            "$COLUMN_SORT_ORDER ASC"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    val mangaUri = cursor.getString(COLUMN_URI)
                    add(
                        MangaItem(
                            name = cursor.getString(COLUMN_NAME),
                            uri = mangaUri,
                            chapters = getChapterSummaries(mangaUri)
                        )
                    )
                }
            }
        }
    }

    fun getChapters(mangaUri: String): List<Chapter> {
        readableDatabase.query(
            TABLE_CHAPTERS,
            arrayOf(COLUMN_URI, COLUMN_NAME),
            "$COLUMN_MANGA_URI = ?",
            arrayOf(mangaUri),
            null,
            null,
            "$COLUMN_SORT_ORDER ASC"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    val chapterUri = cursor.getString(COLUMN_URI)
                    add(
                        Chapter(
                            name = cursor.getString(COLUMN_NAME),
                            uri = chapterUri,
                            images = getPages(chapterUri)
                        )
                    )
                }
            }
        }
    }

    fun saveLibrary(rootUri: String, manga: List<MangaItem>) {
        writableDatabase.transaction {
            LibraryDatabaseSchema.clearLibraryTables(this)
            putMetadata(this, KEY_ROOT_URI, rootUri)
            insertManga(this, manga)
        }
    }

    fun saveChapters(mangaUri: String, mangaName: String, chapters: List<Chapter>) {
        writableDatabase.transaction {
            insertWithOnConflict(
                TABLE_MANGA,
                null,
                ContentValues().apply {
                    put(COLUMN_URI, mangaUri)
                    put(COLUMN_NAME, mangaName)
                    put(COLUMN_SORT_ORDER, getMangaSortOrder(this@transaction, mangaUri))
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            delete(
                TABLE_PAGES,
                "$COLUMN_CHAPTER_URI IN (SELECT $COLUMN_URI FROM $TABLE_CHAPTERS WHERE $COLUMN_MANGA_URI = ?)",
                arrayOf(mangaUri)
            )
            delete(TABLE_CHAPTERS, "$COLUMN_MANGA_URI = ?", arrayOf(mangaUri))
            insertChapters(this, mangaUri, chapters)
        }
    }

    fun clearLibrary() {
        writableDatabase.transaction {
            LibraryDatabaseSchema.clearLibraryTables(this)
            delete(TABLE_METADATA, "$COLUMN_KEY = ?", arrayOf(KEY_ROOT_URI))
        }
    }

    private fun getChapterSummaries(mangaUri: String): List<Chapter> {
        readableDatabase.query(
            TABLE_CHAPTERS,
            arrayOf(COLUMN_URI, COLUMN_NAME),
            "$COLUMN_MANGA_URI = ?",
            arrayOf(mangaUri),
            null,
            null,
            "$COLUMN_SORT_ORDER ASC"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    add(
                        Chapter(
                            name = cursor.getString(COLUMN_NAME),
                            uri = cursor.getString(COLUMN_URI),
                            images = emptyList()
                        )
                    )
                }
            }
        }
    }

    private fun insertManga(db: SQLiteDatabase, manga: List<MangaItem>) {
        manga.forEachIndexed { index, item ->
            db.insertOrThrow(
                TABLE_MANGA,
                null,
                ContentValues().apply {
                    put(COLUMN_URI, item.uri)
                    put(COLUMN_NAME, item.name)
                    put(COLUMN_SORT_ORDER, index)
                }
            )
            insertChapters(db, item.uri, item.chapters)
        }
    }

    private fun insertChapters(db: SQLiteDatabase, mangaUri: String, chapters: List<Chapter>) {
        chapters.forEachIndexed { chapterIndex, chapter ->
            db.insertOrThrow(
                TABLE_CHAPTERS,
                null,
                ContentValues().apply {
                    put(COLUMN_URI, chapter.uri)
                    put(COLUMN_MANGA_URI, mangaUri)
                    put(COLUMN_NAME, chapter.name)
                    put(COLUMN_SORT_ORDER, chapterIndex)
                }
            )
            chapter.images.forEachIndexed { pageIndex, imageUri ->
                db.insertOrThrow(
                    TABLE_PAGES,
                    null,
                    ContentValues().apply {
                        put(COLUMN_CHAPTER_URI, chapter.uri)
                        put(COLUMN_PAGE_URI, imageUri)
                        put(COLUMN_SORT_ORDER, pageIndex)
                    }
                )
            }
        }
    }

    private fun getPages(chapterUri: String): List<String> {
        readableDatabase.query(
            TABLE_PAGES,
            arrayOf(COLUMN_PAGE_URI),
            "$COLUMN_CHAPTER_URI = ?",
            arrayOf(chapterUri),
            null,
            null,
            "$COLUMN_SORT_ORDER ASC"
        ).use { cursor ->
            return buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(COLUMN_PAGE_URI))
                }
            }
        }
    }

    private fun getMangaSortOrder(db: SQLiteDatabase, mangaUri: String): Int {
        db.query(
            TABLE_MANGA,
            arrayOf(COLUMN_SORT_ORDER),
            "$COLUMN_URI = ?",
            arrayOf(mangaUri),
            null,
            null,
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(COLUMN_SORT_ORDER) else Int.MAX_VALUE
        }
    }

    private fun putMetadata(db: SQLiteDatabase, key: String, value: String) {
        db.insertWithOnConflict(
            TABLE_METADATA,
            null,
            ContentValues().apply {
                put(COLUMN_KEY, key)
                put(COLUMN_VALUE, value)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun Cursor.getString(columnName: String): String {
        return getString(getColumnIndexOrThrow(columnName))
    }

    private fun Cursor.getInt(columnName: String): Int {
        return getInt(getColumnIndexOrThrow(columnName))
    }

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }
}
