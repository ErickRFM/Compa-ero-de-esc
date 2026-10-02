package org.companerodeescuela.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.auth.AuthRepository

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
    fun provideAcademicRepository(
        @ApiClient client: HttpClient,
        tokenStore: SessionTokenStore,
        cache: AcademicSnapshotCache,
    ): AcademicRepository = AcademicRepository(
        client = client,
        tokenStore = tokenStore,
        cache = cache,
    )
}
