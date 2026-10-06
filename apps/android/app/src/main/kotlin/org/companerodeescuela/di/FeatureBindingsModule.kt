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
import org.companerodeescuela.core.network.SessionRefreshCoordinator
import org.companerodeescuela.core.security.SessionTokenStore
import org.companerodeescuela.feature.auth.AuthRepository
import org.companerodeescuela.feature.channel.ChannelRepository
import org.companerodeescuela.feature.classroom.ClassroomRepository

@Module
@InstallIn(SingletonComponent::class)
object FeatureBindingsModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        refreshCoordinator: SessionRefreshCoordinator,
    ): AuthRepository = AuthRepository(
        client = client,
        tokenStore = tokenStore,
        refreshCoordinator = refreshCoordinator,
    )

    @Provides
    @Singleton
    fun provideAttendanceRepository(
        localStore: AttendanceLocalStore,
        scheduler: AttendanceSyncScheduler,
        remoteClient: AttendanceRemoteClient,
    ): AttendanceRepository = AttendanceRepository(
        localStore = localStore,
        scheduler = scheduler,
        remoteClient = remoteClient,
    )

    @Provides
    @Singleton
    fun provideAcademicRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        refreshCoordinator: SessionRefreshCoordinator,
        cache: AcademicSnapshotCache,
        personalScheduleStore: PersonalScheduleStore,
    ): AcademicRepository = AcademicRepository(
        client = client,
        tokenStore = tokenStore,
        refreshCoordinator = refreshCoordinator,
        cache = cache,
        personalScheduleStore = personalScheduleStore,
    )

    @Provides
    @Singleton
    fun provideChannelRepository(
        client: HttpClient,
        tokenStore: SessionTokenStore,
        refreshCoordinator: SessionRefreshCoordinator,
    ): ChannelRepository = ChannelRepository(
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
