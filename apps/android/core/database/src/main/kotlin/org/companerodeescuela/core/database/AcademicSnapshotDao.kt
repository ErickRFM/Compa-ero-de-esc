package org.companerodeescuela.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
internal interface AcademicSnapshotDao {

    @Query("SELECT * FROM academic_snapshots WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun find(cacheKey: String): AcademicSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: AcademicSnapshotEntity)

    @Query("DELETE FROM academic_snapshots WHERE cacheKey = :cacheKey")
    suspend fun delete(cacheKey: String)
}
