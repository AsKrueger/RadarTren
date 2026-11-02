package com.askrueger.radartren.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.askrueger.radartren.domain.Core
import com.askrueger.radartren.domain.IncidentCategory
import com.askrueger.radartren.domain.IncidentSource
import com.askrueger.radartren.domain.MetricSample
import com.askrueger.radartren.domain.PeriodType
import com.askrueger.radartren.domain.ReportIncident
import com.askrueger.radartren.domain.Severity
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "incident", indices = [Index("resolvedAt"), Index("firstSeen")])
data class IncidentEntity(
    @PrimaryKey val key: String,
    val source: IncidentSource,
    /** The worst severity reached so far. */
    val severity: Severity,
    val category: IncidentCategory,
    val coreCode: String?,
    /** Line names joined by [LINE_SEPARATOR]. */
    val lines: String,
    val text: String,
    val startedAt: Long,
    val firstSeen: Long,
    val lastSeen: Long,
    val resolvedAt: Long?,
    /** False when it came from a frozen feed: shown with a warning, never notified or counted. */
    val verified: Boolean,
    /** Follows the train on every refresh for train incidents; fixed for the others. */
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val core: Core? get() = Core.entries.firstOrNull { it.code == coreCode }
    val hasLocation: Boolean get() = latitude != null && longitude != null

    fun toReport() = ReportIncident(severity, category, source, core, text, firstSeen, lastSeen, verified)

    companion object {
        const val LINE_SEPARATOR = "|"
    }
}

@Entity(tableName = "metric_sample")
data class MetricSampleEntity(
    @PrimaryKey val at: Long,
    val trains: Int,
    val withDelayInfo: Int,
    val delayed5: Int,
    val delayed15: Int,
    val delaySumMinutes: Int,
    val maxDelayMinutes: Int,
    val cancelled: Int,
) {
    fun toDomain() = MetricSample(at, trains, withDelayInfo, delayed5, delayed15, delaySumMinutes, maxDelayMinutes, cancelled)
}

@Entity(tableName = "report")
data class ReportEntity(
    @PrimaryKey val id: String,
    val type: PeriodType,
    val startEpoch: Long,
    val generatedAt: Long,
    val fileName: String,
    val coverage: Double,
)

@Dao
interface IncidentDao {
    @Query("SELECT * FROM incident WHERE resolvedAt IS NULL")
    suspend fun open(): List<IncidentEntity>

    @Query("SELECT * FROM incident ORDER BY resolvedAt IS NOT NULL, severity DESC, lastSeen DESC LIMIT 300")
    fun observeRecent(): Flow<List<IncidentEntity>>

    @Query("SELECT * FROM incident WHERE key = :key")
    suspend fun find(key: String): IncidentEntity?

    @Upsert
    suspend fun upsert(incident: IncidentEntity)

    @Query("UPDATE incident SET resolvedAt = :at WHERE key IN (:keys)")
    suspend fun resolve(keys: List<String>, at: Long)

    @Query("SELECT * FROM incident WHERE firstSeen < :end AND lastSeen >= :start")
    suspend fun overlapping(start: Long, end: Long): List<IncidentEntity>

    @Query("SELECT * FROM incident ORDER BY firstSeen")
    suspend fun all(): List<IncidentEntity>

    @Query("DELETE FROM incident WHERE lastSeen < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface MetricDao {
    @Insert
    suspend fun insert(sample: MetricSampleEntity)

    @Query("SELECT MAX(at) FROM metric_sample")
    suspend fun latest(): Long?

    @Query("SELECT * FROM metric_sample WHERE at >= :start AND at < :end ORDER BY at")
    suspend fun between(start: Long, end: Long): List<MetricSampleEntity>

    @Query("DELETE FROM metric_sample WHERE at < :before")
    suspend fun deleteOlderThan(before: Long)
}

@Dao
interface ReportDao {
    @Query("SELECT * FROM report ORDER BY startEpoch DESC, type")
    fun observeAll(): Flow<List<ReportEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM report WHERE id = :id)")
    suspend fun exists(id: String): Boolean

    @Upsert
    suspend fun upsert(report: ReportEntity)
}

class Converters {
    @TypeConverter fun severity(value: Severity): Int = value.ordinal
    @TypeConverter fun severity(value: Int): Severity = Severity.entries[value]
    @TypeConverter fun source(value: IncidentSource): String = value.name
    @TypeConverter fun source(value: String): IncidentSource = IncidentSource.valueOf(value)
    @TypeConverter fun category(value: IncidentCategory): String = value.name
    @TypeConverter fun category(value: String): IncidentCategory = IncidentCategory.valueOf(value)
    @TypeConverter fun period(value: PeriodType): String = value.name
    @TypeConverter fun period(value: String): PeriodType = PeriodType.valueOf(value)
}

@Database(
    entities = [IncidentEntity::class, MetricSampleEntity::class, ReportEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
@TypeConverters(Converters::class)
abstract class RadarDatabase : RoomDatabase() {
    abstract fun incidents(): IncidentDao
    abstract fun metrics(): MetricDao
    abstract fun reports(): ReportDao

    companion object {
        fun create(context: Context): RadarDatabase =
            Room.databaseBuilder(context, RadarDatabase::class.java, "radartren.db").build()
    }
}
