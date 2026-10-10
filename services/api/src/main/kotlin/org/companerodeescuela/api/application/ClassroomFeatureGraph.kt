package org.companerodeescuela.api.application

import org.companerodeescuela.api.academic.groups.AcademicGroupRepository
import org.companerodeescuela.api.channel.ChannelAccessPolicy
import org.companerodeescuela.api.channel.ChannelService
import org.companerodeescuela.api.channel.InMemoryChannelRepository
import org.companerodeescuela.api.channel.MongoChannelRepository
import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.classroom.InMemoryClassroomRepository
import org.companerodeescuela.api.classroom.MongoClassroomRepository
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.integrations.academic.AcademicProvider
import org.companerodeescuela.api.representatives.GroupRepresentativeService

data class ClassroomFeatureGraph(
    val classroomService: ClassroomService,
    val channelService: ChannelService,
)

fun buildClassroomFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    academicProvider: AcademicProvider,
    groupRepository: AcademicGroupRepository,
    representativeService: GroupRepresentativeService? = null,
): ClassroomFeatureGraph {
    val classroomRepository = when {
        !settings.hasAuthentication -> InMemoryClassroomRepository()
        settings.mongo.isConfigured -> MongoClassroomRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryClassroomRepository()
        else -> error("Native classrooms require MONGODB_URI outside local development")
    }
    val classroomService = ClassroomService(
        repository = classroomRepository,
        groupRepository = groupRepository,
    )
    val channelRepository = when {
        !settings.hasAuthentication -> InMemoryChannelRepository()
        settings.mongo.isConfigured -> MongoChannelRepository(mongoConnection.database())
        settings.environment == Environment.LOCAL -> InMemoryChannelRepository()
        else -> error("Class channels require MONGODB_URI outside local development")
    }
    return ClassroomFeatureGraph(
        classroomService = classroomService,
        channelService = ChannelService(
            repository = channelRepository,
            accessPolicy = ChannelAccessPolicy(
                academicProvider = academicProvider,
                classroomService = classroomService,
                representativeService = representativeService,
            ),
        ),
    )
}
