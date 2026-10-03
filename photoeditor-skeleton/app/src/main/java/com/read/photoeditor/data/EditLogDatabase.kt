package com.read.photoeditor.data

import androidx.room.*
import com.read.photoeditor.data.model.EditLogEntry

@Entity(tableName = "edit_log")
data class EditLogRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val photoId: Long,
    val styleSummary: String,
    val initialParams: String,
    val finalParams: String,
    val wasCorrected: Boolean,
    val correctionNote: String?,
    val timestampMillis: Long
)

@Dao
interface EditLogDao {
    @Insert
    suspend fun insert(row: EditLogRow): Long

    @Query("SELECT * FROM edit_log ORDER BY timestampMillis DESC")
    suspend fun getAll(): List<EditLogRow>

    @Query("SELECT COUNT(*) FROM edit_log")
    suspend fun count(): Int

    // Once we're ready for Path B, this is the export point for building a
    // training set: (photo -> style -> accepted final params).
    @Query("SELECT * FROM edit_log WHERE wasCorrected = 1")
    suspend fun getCorrectedExamples(): List<EditLogRow>
}

@Database(entities = [EditLogRow::class], version = 1)
abstract class EditLogDatabase : RoomDatabase() {
    abstract fun editLogDao(): EditLogDao
}

/**
 * Thin wrapper so the rest of the app logs through a simple API without
 * knowing about Room directly. This is the "data gathering runs from day one,
 * training pipeline comes later" piece we agreed on.
 */
class EditLogRepository(private val dao: EditLogDao) {

    suspend fun logEdit(entry: EditLogEntry) {
        dao.insert(
            EditLogRow(
                photoId = entry.photoId,
                styleSummary = entry.styleSummary,
                initialParams = entry.initialParams,
                finalParams = entry.finalParams,
                wasCorrected = entry.wasCorrected,
                correctionNote = entry.correctionNote,
                timestampMillis = entry.timestampMillis
            )
        )
    }

    suspend fun datasetSize(): Int = dao.count()
}
