package org.companerodeescuela.feature.tutoring

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.security.SessionTokenStore

@Module
@InstallIn(SingletonComponent::class)
object TutorBindingsModule {
    @Provides
    @Singleton
    fun provideTutorRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        refreshCoordinator: SessionRefreshCoordinator,
    ): TutorRepository = TutorRepository(client, tokenStore, refreshCoordinator)
}
