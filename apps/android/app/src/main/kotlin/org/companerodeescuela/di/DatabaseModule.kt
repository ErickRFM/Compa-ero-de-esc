package org.companerodeescuela.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.AcademicSnapshotCacheFactory
import org.companerodeescuela.core.database.CompaneroDatabase
import org.companerodeescuela.core.database.CompaneroDatabaseFactory
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.AttendanceLocalStoreFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CompaneroDatabase = CompaneroDatabaseFactory.create(context)

    @Provides
    @Singleton
    fun provideAttendanceLocalStore(
        database: CompaneroDatabase,
    ): AttendanceLocalStore = AttendanceLocalStoreFactory.create(database)

    @Provides
    @Singleton
    fun provideAcademicSnapshotCache(
        database: CompaneroDatabase,
    ): AcademicSnapshotCache = AcademicSnapshotCacheFactory.create(database)
}
