package org.companerodeescuela.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import org.companerodeescuela.core.academic.AcademicRepository
import org.companerodeescuela.core.academic.PersonalScheduleRepository
import org.companerodeescuela.core.attendance.AttendanceRemoteClient
import org.companerodeescuela.core.attendance.AttendanceRepository
import org.companerodeescuela.core.attendance.AttendanceSyncScheduler
import org.companerodeescuela.core.database.AcademicSnapshotCache
import org.companerodeescuela.core.database.AttendanceLocalStore
import org.companerodeescuela.core.database.PersonalScheduleStore
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.auth.AuthRepository
import org.companerodeescuela.feature.channel.ChannelRepository

@Module
@InstallIn(SingletonComponent::class)
object FeatureBindingsModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
    ): AuthRepository = AuthRepository(
        client = client,
        tokenStore = tokenStore,
    )

    @Provides
    @Singleton
    fun provideAttendanceRepository(
        tokenStore: SessionTokenStore,
        localStore: AttendanceLocalStore,
        scheduler: AttendanceSyncScheduler,
        remoteClient: AttendanceRemoteClient,
    ): AttendanceRepository = AttendanceRepository(
        tokenStore = tokenStore,
        localStore = localStore,
        scheduler = scheduler,
        remoteClient = remoteClient,
    )

    @Provides
    @Singleton
    fun provideAcademicRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        cache: AcademicSnapshotCache,
        personalScheduleStore: PersonalScheduleStore,
    ): AcademicRepository = AcademicRepository(
        client = client,
        tokenStore = tokenStore,
        cache = cache,
        personalScheduleStore = personalScheduleStore,
    )

    @Provides
    @Singleton
    fun provideChannelRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
    ): ChannelRepository = ChannelRepository(
        client = client,
        tokenStore = tokenStore,
    )

    @Provides
    @Singleton
    fun providePersonalScheduleRepository(
        tokenStore: SessionTokenStore,
        personalScheduleStore: PersonalScheduleStore,
    ): PersonalScheduleRepository = PersonalScheduleRepository(
        tokenStore = tokenStore,
        store = personalScheduleStore,
    )
}
