package org.companerodeescuela.feature.classroom

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
object ClassroomBindingsModule {
    @Provides
    @Singleton
    fun provideAcademicGroupRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        refreshCoordinator: SessionRefreshCoordinator,
    ): AcademicGroupRepository = AcademicGroupRepository(
        client = client,
        tokenStore = tokenStore,
        refreshCoordinator = refreshCoordinator,
    )

    @Provides
    @Singleton
    fun provideClassroomRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        refreshCoordinator: SessionRefreshCoordinator,
    ): ClassroomRepository = ClassroomRepository(
        client = client,
        tokenStore = tokenStore,
        refreshCoordinator = refreshCoordinator,
    )
}
