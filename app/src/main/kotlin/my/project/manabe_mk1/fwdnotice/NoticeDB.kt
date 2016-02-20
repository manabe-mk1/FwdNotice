package my.project.manabe_mk1.fwdnotice

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import android.service.notification.StatusBarNotification

import java.text.SimpleDateFormat
import java.util.*

val nameNoticeDB    = "fwd-notice.db"
val versionNoticeDB = 1

class NoticeDb(context: Context?, version: Int) : SQLiteOpenHelper(context, nameNoticeDB, null, version)
{
    private fun String.stripHeredoc(): String {
        return query.stripHeredoc(this)
    }

    private object query {
        inline fun stripHeredoc(string: String): String {
            return string.replace(Regex("\n\\s*"), "\n").replace(Regex("^\\s"), "").replace(Regex("\\s$"), "")
        }
        private fun String.stripHereDoc(): String {
            return query.stripHeredoc(this)
        }

        val createNotice = """
            CREATE TABLE notice (
                notice_id       INTEGER PRIMARY KEY AUTOINCREMENT,
                android_id      INTEGER NOT NULL DEFAULT 0,
                package_id      INTEGER NOT NULL DEFAULT 0,
                is_clearable    INTEGER NOT NULL DEFAULT 0,
                is_ongoing      INTEGER NOT NULL DEFAULT 0,
                ticker_message  TEXT    NOT NULL DEFAULT "",
                created         INTEGER NOT NULL DEFAULT 0
                );
        """.stripHereDoc()

        val createPackage = """
            CREATE TABLE package (
                package_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                package_name    VARCHAR(255) NOT NULL UNIQUE DEFAULT ""
            );
        """.stripHereDoc()
    }

    /**
     * マイグレーション用処理
     */
    interface Patch {
        fun apply (db: SQLiteDatabase)
        fun revert(db: SQLiteDatabase)
    }

    companion object {
        val patches = listOf<Patch>(
                object : Patch {
                    override fun apply (db: SQLiteDatabase) {
                        db.execSQL(query.createNotice)
                    }
                    override fun revert(db: SQLiteDatabase) {
                        db.execSQL("DROP TABLE notice")
                    }
                },
                object : Patch {
                    override fun apply (db: SQLiteDatabase) {
                        db.execSQL(query.createPackage)
                    }
                    override fun revert(db: SQLiteDatabase) {
                        db.execSQL("DROP TABLE package")
                    }
                }
                , object : Patch {
                    override fun apply (db: SQLiteDatabase) {
                        db.execSQL("ALTER TABLE package ADD COLUMN is_forward INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE package ADD COLUMN is_ignore  INTEGER NOT NULL DEFAULT 0")
                    }
                    override fun revert(db: SQLiteDatabase) {
                        db.execSQL("ALTER TABLE package DROP COLUMN is_forward")
                        db.execSQL("ALTER TABLE package DROP COLUMN is_ignore")
                    }
                }
                //, object : Patch {
                //    override fun apply (db: SQLiteDatabase) { Log.d("dbPatch", "3 -> 4") }
                //    override fun revert(db: SQLiteDatabase) { Log.d("dbPatch", "4 -> 3") }
                //}
                //, object : Patch {
                //    override fun apply (db: SQLiteDatabase) { Log.d("dbPatch", "4 -> 5") }
                //    override fun revert(db: SQLiteDatabase) { Log.d("dbPatch", "5 -> 4") }
                //}
        )

        fun getDb(context: Context): NoticeDb {
            // ダウングレードを促す場合、要素の削減は不可。patches.size を固定数値に変更する
            return NoticeDb(context, patches.size)
        }
    }

    override fun onCreate(db: SQLiteDatabase?) {
        db ?: throw IllegalArgumentException("SQLiteDatabase is not init.")
        Log.d("DB create", "init -> ${patches.size}")
        onUpgrade(db, 0, patches.size)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db ?: throw IllegalArgumentException("SQLiteDatabase is not init.")
        Log.d("DB upgrade", "$oldVersion -> $newVersion")
        for(patch in patches.subList(oldVersion, newVersion)) {
            patch.apply(db)
        }
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db ?: throw IllegalArgumentException("SQLiteDatabase is not init.")
        Log.d("DB downgrade", "$newVersion -> $oldVersion")
        for(patch in patches.subList(newVersion, oldVersion).reversed()) {
            patch.revert(db)
        }
    }

    //
    // ここまでDB初期化処理
    //

    class Notice(cursor: Cursor) {
        private val record = cursor

        val noticeId        = record.getInt(record.getColumnIndex("notice_id"))
        val androidId       = record.getInt(record.getColumnIndex("android_id"))
        val packageName     = record.getString(record.getColumnIndex("package_name"))
        val tickerMessage   = record.getString(record.getColumnIndex("ticker_message"))
        val isClearable     = (record.getInt(record.getColumnIndex("is_clearable")) > 0)
        val isOngoing       = (record.getInt(record.getColumnIndex("is_ongoing")) > 0)
        val created         = Date(record.getLong(record.getColumnIndex("created")))

        override fun toString(): String {
            return "[%s] %s(%s[%d%s%s])".format(
                    SimpleDateFormat("yy/MM/dd hh:mm:ss").format(created),
                    tickerMessage, packageName, androidId,
                    if(isClearable) ":C" else "", if(isOngoing) ":G" else ""
            )
        }
    }

    /**
     * 強制無視
     */
    private val exclude = listOf (
            "com.android.providers.downloads"
            //, "com.sonymobile.runtimeskinning.core"
            //, "com.sonyericsson.organizer"
    )

    fun insertNotice(notice: StatusBarNotification): Pair<Pair<Long, Long>, Boolean> {
        if(exclude.contains(notice.packageName)) {
            return Pair(Pair(0L, 0L), false)
        }

        val db = writableDatabase

        var packageId = 0L
        var isDisplay = false
        var isForward = false
        var newPackageId = 0L
        var cursor = db.rawQuery(
                "SELECT * FROM package WHERE package_name = ? ORDER BY package_id ASC LIMIT 1",
                arrayOf("${notice.packageName}"))

        if(cursor.moveToFirst()) {
            packageId = cursor.getLong(cursor.getColumnIndex("package_id"))
            isDisplay = cursor.getInt(cursor.getColumnIndex("is_ignore")) <= 0
            isForward = cursor.getInt(cursor.getColumnIndex("is_forward")) > 0
            cursor.close()

        } else {
            isDisplay = true

            var rowPackage = ContentValues()
            rowPackage.put("package_name", "${notice.packageName}")
            rowPackage.put("is_ignore", if(isDisplay) 0 else 1)
            packageId = db.insert("package", null, rowPackage)
            newPackageId = packageId
            Log.d("insertPackage", "${packageId}")
        }

        var noticeId = 0L
        if(isDisplay) {
            var rowNotice = ContentValues()
            rowNotice.put("android_id",     notice.id)
            rowNotice.put("package_id",     packageId)
            rowNotice.put("is_clearable",   if(notice.isClearable) 1 else 0)
            rowNotice.put("is_ongoing",     if(notice.isOngoing)   1 else 0)
            rowNotice.put("ticker_message", "${notice.notification.tickerText}")
            rowNotice.put("created",        System.currentTimeMillis())
            noticeId = db.insert("notice", null, rowNotice)
        }

        db.close()
        return Pair(Pair(noticeId, newPackageId), isForward)
    }

    fun selectNoticeLimit(func: (cursor: Cursor) -> Unit): Unit {
        val db = readableDatabase
        val cursor = db.rawQuery(
                """
                SELECT * FROM (
                    SELECT *
                    FROM notice
                    INNER JOIN package ON package.package_id = notice.package_id
                    WHERE NOT package.is_ignore
                    ORDER BY notice_id DESC
                    LIMIT 10
                ) AS notice10 ORDER BY notice_id ASC
                """.stripHeredoc(),
                arrayOf())
        if(cursor.moveToFirst()) {
            do {
                func(cursor)
            } while(cursor.moveToNext())
        }
        cursor.close()
        db.close()
    }

    fun selectNoticeById(noticeId: Long, func: (cursor: Cursor, dummy: String) -> Unit): Unit {
        val db = readableDatabase
        val cursor = db.rawQuery(
                """
                SELECT *
                FROM notice
                INNER JOIN package ON package.package_id = notice.package_id
                WHERE notice_id = ?
                AND NOT package.is_ignore
                LIMIT 1
                """.stripHeredoc(),
                arrayOf("${noticeId}"))
        if(cursor.moveToFirst()) {
            func(cursor, "This is test.")
        }
        cursor.close()
        db.close()
    }

    class Package(cursor: Cursor) {
        private val record = cursor

        val packageId   = record.getInt(record.getColumnIndex("package_id"))
        val packageName = record.getString(record.getColumnIndex("package_name"))
        val isForward   = (record.getInt(record.getColumnIndex("is_forward")) > 0)
        val isIgnore    = (record.getInt(record.getColumnIndex("is_ignore")) > 0)

        override fun toString(): String {
            return "%s(%d)".format(packageName, packageId)
        }
    }

    fun updatePackageForward(id: Int, isForward: Boolean) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE package SET is_forward = ? WHERE package_id = ?",
            arrayOf(isForward, id)
        )
        db.close()
    }

    fun updatePackageIgnore(id: Int, isIgnore: Boolean) {
        val db = writableDatabase
        db.execSQL(
            "UPDATE package SET is_ignore = ? WHERE package_id = ?",
            arrayOf(isIgnore, id)
        )
        db.close()
    }

    fun selectPackageList(func: (cursor: Cursor) -> Unit): Unit {
        val db = readableDatabase
        val cursor = db.rawQuery(
                """
                SELECT *
                FROM package
                ORDER BY package_id ASC
                """.stripHeredoc(),
                arrayOf())
        if(cursor.moveToFirst()) {
            do {
                func(cursor)
            } while(cursor.moveToNext())
        }
        cursor.close()
        db.close()
    }

    fun selectPackageById(packageId: Long, func: (cursor: Cursor) -> Unit): Unit {
        val db = readableDatabase
        val cursor = db.rawQuery(
                """
                SELECT *
                FROM package
                WHERE package_id = ?
                LIMIT 1
                """.stripHeredoc(),
                arrayOf("$packageId"))
        if(cursor.moveToFirst()) {
            func(cursor)
        }
        cursor.close()
        db.close()
    }
}