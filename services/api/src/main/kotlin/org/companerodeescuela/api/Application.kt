package org.companerodeescuela.api

import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import org.companerodeescuela.api.application.module
import org.companerodeescuela.api.config.ConfigurationException
import org.companerodeescuela.api.config.SettingsLoader
import org.companerodeescuela.api.database.DriverMongoConnectionFactory
import org.slf4j.LoggerFactory
import kotlin.system.exitProcess

/**
 * Process entry point.
 *
 * Responsibilities are limited to: load configuration, build the graph, start
 * the server, and translate startup failures into a clear log line plus a
 * non-zero exit code. All request handling lives in [module].
 */
fun main() {
    val log = LoggerFactory.getLogger("org.companerodeescuela.api.Application")

    val settings = try {
        SettingsLoader(env = System::getenv).load()
    } catch (error: ConfigurationException) {
        log.error("Cannot start: invalid configuration. {}", error.message)
        exitProcess(1)
    }

    val mongoConnection = DriverMongoConnectionFactory().create(settings.mongo)

    log.info(
        "Starting {} v{} in {} on {}:{} (mongo configured: {})",
        settings.serviceName,
        settings.version,
        settings.environment,
        settings.host,
        settings.port,
        settings.mongo.isConfigured,
    )

    embeddedServer(
        factory = CIO,
        host = settings.host,
        port = settings.port,
        module = { module(settings, mongoConnection) },
    ).start(wait = true)
}
