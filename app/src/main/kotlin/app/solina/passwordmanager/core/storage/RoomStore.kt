package app.solina.passwordmanager.core.storage

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
import androidx.room.withTransaction

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

    @Query("DELETE FROM history WHERE itemId = :itemId")
    suspend fun clearHistory(itemId: String)

    @Query("SELECT * FROM history WHERE itemId = :itemId ORDER BY rev DESC")
    suspend fun history(itemId: String): List<HistoryRow>

    @Query("DELETE FROM items")
    suspend fun wipeItems()

    @Query("DELETE FROM history")
    suspend fun wipeHistory()
}

@Database(entities = [ItemRow::class, HistoryRow::class], version = 1, exportSchema = false)
abstract class SolinaDatabase : RoomDatabase() {
    abstract fun dao(): ItemDao

    companion object {
        fun open(context: Context): SolinaDatabase =
            Room.databaseBuilder(context.applicationContext, SolinaDatabase::class.java, "solina.db").build()
    }
}

class RoomItemStore(private val db: SolinaDatabase) : ItemStore {
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

    /** Batch persistence is one Room transaction: a failure rolls back every inserted row. */
    override suspend fun saveAll(items: List<StoredItem>) {
        if (items.isEmpty()) return
        db.withTransaction {
            items.forEach { item ->
                dao.upsert(ItemRow(item.id, item.rev, item.updatedAt, item.deletedAt, item.wrappedItemKey, item.blob))
            }
        }
    }

    override suspend fun delete(id: String, deletedAt: Long, newRev: Long) {
        db.withTransaction {
            dao.clearHistory(id)
            dao.upsert(ItemRow(id, newRev, deletedAt, deletedAt, ByteArray(0), ByteArray(0)))
        }
    }

    override suspend fun history(itemId: String) =
        dao.history(itemId).map { StoredHistory(it.itemId, it.rev, it.savedAt, it.wrappedItemKey, it.blob) }

    override suspend fun wipeAll() {
        db.withTransaction { dao.wipeItems(); dao.wipeHistory() }
    }

    private fun ItemRow.toStored() = StoredItem(id, rev, updatedAt, deletedAt, wrappedItemKey, blob)
}
