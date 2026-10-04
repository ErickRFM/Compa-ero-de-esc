package org.companerodeescuela.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
internal interface PersonalScheduleDao {
    @Query(
        """
        SELECT * FROM personal_schedule_entries
        WHERE ownerId = :ownerId
        ORDER BY
            CASE dayOfWeek
                WHEN 'MONDAY' THEN 1
                WHEN 'TUESDAY' THEN 2
                WHEN 'WEDNESDAY' THEN 3
                WHEN 'THURSDAY' THEN 4
                WHEN 'FRIDAY' THEN 5
                WHEN 'SATURDAY' THEN 6
                WHEN 'SUNDAY' THEN 7
                ELSE 8
            END,
            startsAt,
            subjectName
        """,
    )
    suspend fun list(ownerId: String): List<PersonalScheduleEntity>

    @Upsert
    suspend fun upsert(entry: PersonalScheduleEntity)

    @Upsert
    suspend fun upsertAll(entries: List<PersonalScheduleEntity>)

    @Query("DELETE FROM personal_schedule_entries WHERE ownerId = :ownerId AND id = :id")
    suspend fun delete(ownerId: String, id: String)

    @Query(
        "DELETE FROM personal_schedule_entries " +
            "WHERE ownerId = :ownerId AND source = :source",
    )
    suspend fun deleteBySource(ownerId: String, source: String)

    @Transaction
    suspend fun replaceBySource(
        ownerId: String,
        source: String,
        entries: List<PersonalScheduleEntity>,
    ) {
        deleteBySource(ownerId, source)
        upsertAll(entries)
    }
}
