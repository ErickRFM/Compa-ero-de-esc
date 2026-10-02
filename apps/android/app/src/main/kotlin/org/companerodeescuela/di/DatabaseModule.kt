package org.companerodeescuela.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.AcademicSnapshotCacheFactory
import org.companerodeescuela.core.database.CompaneroDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): CompaneroDatabase =
        Room.databaseBuilder(
            context,
            CompaneroDatabase::class.java,
            "companero-cache.db",
        ).build()

    @Provides
    @Singleton
    fun provideAcademicSnapshotCache(
        database: CompaneroDatabase,
    ): AcademicSnapshotCache = AcademicSnapshotCacheFactory.create(database)
}
