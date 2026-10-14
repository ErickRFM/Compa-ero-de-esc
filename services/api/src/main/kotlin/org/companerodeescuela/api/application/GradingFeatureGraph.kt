package org.companerodeescuela.api.application

import org.companerodeescuela.api.classroom.ClassroomService
import org.companerodeescuela.api.config.ApiSettings
import org.companerodeescuela.api.config.Environment
import org.companerodeescuela.api.database.MongoConnection
import org.companerodeescuela.api.grading.GradebookRepository
import org.companerodeescuela.api.grading.GradebookService
import org.companerodeescuela.api.grading.InMemoryGradebookRepository
import org.companerodeescuela.api.grading.MongoGradebookRepository

data class GradingFeatureGraph(
    val gradebookService: GradebookService,
)

fun buildGradingFeatureGraph(
    settings: ApiSettings,
    mongoConnection: MongoConnection,
    classrooms: ClassroomService,
): GradingFeatureGraph {
    val repository: GradebookRepository =
        when {
            !settings.hasAuthentication -> InMemoryGradebookRepository()
            settings.mongo.isConfigured -> MongoGradebookRepository(mongoConnection.database())
            settings.environment == Environment.LOCAL -> InMemoryGradebookRepository()
            else -> error("Gradebooks require MONGODB_URI outside local development")
        }
    return GradingFeatureGraph(GradebookService(classrooms, repository))
}
