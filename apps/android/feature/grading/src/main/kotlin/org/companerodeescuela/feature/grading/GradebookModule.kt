package org.companerodeescuela.feature.grading

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.security.SessionTokenStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GradebookModule {
    @Provides
    @Singleton
    fun provideGradebookRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        refreshCoordinator: SessionRefreshCoordinator,
    ): GradebookRepository = GradebookRepository(client, tokenStore, refreshCoordinator)
}
