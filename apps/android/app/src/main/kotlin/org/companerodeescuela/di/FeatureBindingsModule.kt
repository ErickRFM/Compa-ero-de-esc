package org.companerodeescuela.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.auth.AuthRepository
import org.companerodeescuela.feature.home.AcademicHomeRepository
import org.companerodeescuela.feature.schedule.ScheduleRepository

@Module
@InstallIn(SingletonComponent::class)
object FeatureBindingsModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApiClient client: HttpClient,
        tokenStore: SessionTokenStore,
    ): AuthRepository = AuthRepository(
        client = client,
        tokenStore = tokenStore,
    )

    @Provides
    @Singleton
    fun provideAcademicHomeRepository(
        @ApiClient client: HttpClient,
        tokenStore: SessionTokenStore,
        cache: AcademicSnapshotCache,
    ): AcademicHomeRepository = AcademicHomeRepository(
        client = client,
        tokenStore = tokenStore,
        cache = cache,
    )

    @Provides
    @Singleton
    fun provideScheduleRepository(
        cache: AcademicSnapshotCache,
    ): ScheduleRepository = ScheduleRepository(cache)
}
