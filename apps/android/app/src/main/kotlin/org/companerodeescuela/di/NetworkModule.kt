package org.companerodeescuela.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import org.companerodeescuela.core.network.ApiEnvironment
import org.companerodeescuela.core.network.createApiClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /**
     * Base URL.
     *
     * The emulator reaches the host machine on 10.0.2.2. Release builds get the
     * same value here only because there is no real deployment yet; that
     * placeholder is deliberately visible in one obvious place instead of
     * scattered through a resources file.
     */
    @Provides
    @Singleton
    fun provideApiEnvironment(): ApiEnvironment = ApiEnvironment.Emulator

    @Provides
    @Singleton
    fun provideApiClient(environment: ApiEnvironment): HttpClient =
        createApiClient(environment)
}
