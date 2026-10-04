package org.companerodeescuela.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import org.companerodeescuela.BuildConfig
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Base URL embedded at build time.
     *
     * Local builds default to the emulator host. Release/test builds can point
     * to a deployed backend by exporting COMPANERO_API_BASE_URL before Gradle.
     */
    @Provides
    @Singleton
    fun provideApiEnvironment(): ApiEnvironment =
        ApiEnvironment(
            baseUrl = BuildConfig.API_BASE_URL,
            name = "build-config",
        )

    @Provides
    @Singleton
    fun provideApiClient(environment: ApiEnvironment): HttpClient =
        createApiClient(environment)
}
