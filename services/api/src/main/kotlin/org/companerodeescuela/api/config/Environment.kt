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
        /**
         * Parses [raw] into an [Environment], defaulting to [LOCAL] when the
         * value is absent and failing fast when the value is present but
         * unknown. A typo in `APP_ENV` must never silently downgrade the
         * runtime to a non-production profile in a real deployment.
         */
        fun parse(raw: String?): Environment = when (raw?.trim()?.lowercase()) {
            null, "" -> LOCAL
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
