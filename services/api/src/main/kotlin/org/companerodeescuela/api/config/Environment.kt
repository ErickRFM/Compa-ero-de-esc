package org.companerodeescuela.api.config

/**
 * Deployment environment the process is running in.
 *
 * The value drives log verbosity, how loudly configuration problems are
 * reported, and whether features that are unsafe outside production are
 * allowed to run.
 */
enum class Environment {
    LOCAL,
    DEVELOPMENT,
    STAGING,
    PRODUCTION,
    ;

    val isProduction: Boolean
        get() = this == PRODUCTION

    companion object {
        /** Parses [raw] into an [Environment], failing fast when it is absent or unknown. */
        fun parse(raw: String?): Environment = when (raw?.trim()?.lowercase()) {
            null, "" -> throw ConfigurationException("APP_ENV is required: local, development, staging, or production")
            "local" -> LOCAL
            "dev", "development" -> DEVELOPMENT
            "staging", "stage" -> STAGING
            "prod", "production" -> PRODUCTION
            else -> throw ConfigurationException(
                "APP_ENV must be one of: local, development, staging, production",
            )
        }
    }
}
