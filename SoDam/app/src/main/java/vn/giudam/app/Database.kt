package vn.giudam.app

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "events")
data class EventRecord(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val date: String = "", val note: String = "")

@Entity(tableName = "contacts")
data class GuestRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nickname: String = "",
    val address: String = "",
    val phone: String = "",
    val amount: Long = 0,
    val occasion: String = "Khác",
    val date: String = "",
    val note: String = "",
    val eventId: Long = 1
)

@Dao
interface GuestDao {
    @Query("SELECT * FROM contacts WHERE eventId = :eventId ORDER BY name COLLATE NOCASE ASC") fun all(eventId: Long): Flow<List<GuestRecord>>
    @Query("SELECT * FROM contacts WHERE eventId = :eventId AND (name LIKE '%' || :q || '%' OR nickname LIKE '%' || :q || '%' OR address LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR occasion LIKE '%' || :q || '%' OR note LIKE '%' || :q || '%') ORDER BY name COLLATE NOCASE") fun search(eventId: Long, q: String): Flow<List<GuestRecord>>
    @Insert suspend fun insert(record: GuestRecord)
    @Update suspend fun update(record: GuestRecord)
    @Delete suspend fun delete(record: GuestRecord)
    @Query("SELECT * FROM contacts") suspend fun snapshot(): List<GuestRecord>
    @Query("DELETE FROM contacts") suspend fun clear()
    @Query("DELETE FROM contacts WHERE eventId = :eventId") suspend fun deleteGuestsForEvent(eventId: Long)
    @Query("SELECT * FROM events ORDER BY id DESC") fun events(): Flow<List<EventRecord>>
    @Insert suspend fun insertEvent(event: EventRecord): Long
    @Update suspend fun updateEvent(event: EventRecord)
    @Delete suspend fun deleteEvent(event: EventRecord)
    @Query("SELECT COUNT(*) FROM events") suspend fun eventCount(): Int
    @Query("SELECT * FROM events") suspend fun eventSnapshot(): List<EventRecord>
    @Query("DELETE FROM events") suspend fun clearEvents()
}

@Database(entities = [GuestRecord::class, EventRecord::class], version = 2, exportSchema = false)
abstract class GuestDb : RoomDatabase() {
    abstract fun dao(): GuestDao
    companion object {
        @Volatile private var INSTANCE: GuestDb? = null
        fun get(context: Context): GuestDb = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, GuestDb::class.java, "giudam_offline.db")
                .addMigrations(object : Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, date TEXT NOT NULL, note TEXT NOT NULL)")
                        db.execSQL("INSERT INTO events (id, name, date, note) VALUES (1, 'Sổ cũ / chưa phân loại', '', '')")
                        db.execSQL("ALTER TABLE contacts ADD COLUMN eventId INTEGER NOT NULL DEFAULT 1")
                    }
                })
                .build().also { INSTANCE = it }
        }
    }
}
