package org.companerodeescuela.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.core.security.SessionTokenStoreFactory

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides
    @Singleton
    fun provideSessionTokenStore(
        @ApplicationContext context: Context,
    ): SessionTokenStore = SessionTokenStoreFactory.create(context)
}
