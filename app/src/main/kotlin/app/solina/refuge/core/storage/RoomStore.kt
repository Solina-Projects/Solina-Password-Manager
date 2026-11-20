package app.solina.refuge.core.storage

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "items")
class ItemRow(
    @PrimaryKey val id: String,
    val rev: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val wrappedItemKey: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val blob: ByteArray,
)

@Entity(tableName = "history", indices = [Index("itemId")])
class HistoryRow(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val itemId: String,
    val rev: Long,
    val savedAt: Long,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val wrappedItemKey: ByteArray,
    @ColumnInfo(typeAffinity = ColumnInfo.BLOB) val blob: ByteArray,
)

/**
 * فرادادهٔ متن‌باز موعد کار (فاز ۳). فقط شناسهٔ کار، زمان موعد و زمان آخرین یادآوری؛
 * هیچ عنوان، متن یا محتوای دیگری اینجا ذخیره نمی‌شود — محتوا فقط داخل blob رمز خزانه است.
 */
@Entity(tableName = "task_due")
class TaskDueRow(
    @PrimaryKey val id: String,
    val dueAt: Long,
    val notifiedAt: Long,
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items")
    suspend fun all(): List<ItemRow>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemRow?

    @Upsert
    suspend fun upsert(row: ItemRow)

    @Query("INSERT INTO history (itemId, rev, savedAt, wrappedItemKey, blob) VALUES (:itemId, :rev, :savedAt, :key, :blob)")
    suspend fun addHistory(itemId: String, rev: Long, savedAt: Long, key: ByteArray, blob: ByteArray)

    @Query(
        "DELETE FROM history WHERE itemId = :itemId AND rowId NOT IN " +
            "(SELECT rowId FROM history WHERE itemId = :itemId ORDER BY rev DESC, rowId DESC LIMIT :keep)",
    )
    suspend fun trimHistory(itemId: String, keep: Int)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM history WHERE itemId = :itemId")
    suspend fun clearHistory(itemId: String)

    @Query("SELECT * FROM history WHERE itemId = :itemId ORDER BY rev DESC")
    suspend fun history(itemId: String): List<HistoryRow>

    @Query("DELETE FROM items")
    suspend fun wipeItems()

    @Query("DELETE FROM history")
    suspend fun wipeHistory()

    @Upsert
    suspend fun upsertTaskDue(row: TaskDueRow)

    @Query("DELETE FROM task_due WHERE id = :id")
    suspend fun deleteTaskDue(id: String)

    @Query("SELECT * FROM task_due")
    suspend fun allTaskDues(): List<TaskDueRow>

    @Query("DELETE FROM task_due")
    suspend fun wipeTaskDues()
}

@Database(entities = [ItemRow::class, HistoryRow::class, TaskDueRow::class], version = 2, exportSchema = false)
abstract class RefugeDatabase : RoomDatabase() {
    abstract fun dao(): ItemDao

    companion object {
        /** مهاجرت ۱ ← ۲ (فاز ۳): فقط جدول متن‌باز موعد کارها اضافه می‌شود؛ جدول items دست نمی‌خورد. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `task_due` (`id` TEXT NOT NULL, `dueAt` INTEGER NOT NULL, `notifiedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }

        fun open(context: Context): RefugeDatabase =
            Room.databaseBuilder(context.applicationContext, RefugeDatabase::class.java, "refuge.db")
                .addMigrations(MIGRATION_1_2)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // بازنویسی صفحات حذف‌شده با صفر برای کاهش باقی‌ماندهٔ متادیتا/بلاب در دیسک
                        // نکته: PRAGMA حتی موقع مقداردهی یک ردیف برمی‌گرداند، پس execSQL روی اندروید واقعی SQLiteException می‌اندازد؛ با query ردیف مصرف می‌شود.
                        db.query("PRAGMA secure_delete = ON").close()
                    }
                })
                .build()
    }
}

class RoomItemStore(private val db: RefugeDatabase) : ItemStore {
    private val dao = db.dao()

    override suspend fun all() = dao.all().map { it.toStored() }
    override suspend fun get(id: String) = dao.get(id)?.toStored()

    override suspend fun save(item: StoredItem, archivedPrevious: StoredHistory?, keepHistory: Int) {
        db.withTransaction {
            if (archivedPrevious != null) {
                dao.addHistory(
                    archivedPrevious.itemId, archivedPrevious.rev, archivedPrevious.savedAt,
                    archivedPrevious.wrappedItemKey, archivedPrevious.blob,
                )
                dao.trimHistory(item.id, keepHistory)
            }
            dao.upsert(ItemRow(item.id, item.rev, item.updatedAt, item.deletedAt, item.wrappedItemKey, item.blob))
        }
    }

    override suspend fun delete(id: String, deletedAt: Long, newRev: Long, archivedPrevious: StoredHistory?) {
        db.withTransaction {
            if (archivedPrevious != null) {
                dao.addHistory(
                    archivedPrevious.itemId, archivedPrevious.rev, archivedPrevious.savedAt,
                    archivedPrevious.wrappedItemKey, archivedPrevious.blob,
                )
                dao.trimHistory(id, VaultRepository.MAX_HISTORY)
            }
            dao.upsert(ItemRow(id, newRev, deletedAt, deletedAt, ByteArray(0), ByteArray(0)))
        }
    }

    override suspend fun purge(id: String) {
        db.withTransaction {
            dao.clearHistory(id)
            dao.deleteItem(id)
        }
    }

    override suspend fun history(itemId: String) =
        dao.history(itemId).map { StoredHistory(it.itemId, it.rev, it.savedAt, it.wrappedItemKey, it.blob) }

    override suspend fun wipeAll() {
        db.withTransaction { dao.wipeItems(); dao.wipeHistory(); dao.wipeTaskDues() }
        // پاکسازی کامل WAL و صفحات آزاد فایل دیتابیس
        runCatching {
            db.openHelper.writableDatabase.apply {
                query("PRAGMA wal_checkpoint(TRUNCATE)").close()
                execSQL("VACUUM")
            }
        }
    }

    override suspend fun saveTaskDue(id: String, dueAt: Long, notifiedAt: Long) {
        dao.upsertTaskDue(TaskDueRow(id, dueAt, notifiedAt))
    }

    override suspend fun deleteTaskDue(id: String) {
        dao.deleteTaskDue(id)
    }

    override suspend fun allTaskDues(): List<TaskDueMeta> =
        dao.allTaskDues().map { TaskDueMeta(it.id, it.dueAt, it.notifiedAt) }

    private fun ItemRow.toStored() = StoredItem(id, rev, updatedAt, deletedAt, wrappedItemKey, blob)
}
