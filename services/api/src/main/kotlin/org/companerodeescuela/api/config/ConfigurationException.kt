package org.companerodeescuela.api.config

/**
 * Raised when the process is started with configuration that cannot work.
 *
 * These are startup-time programming/operations errors, not request errors:
 * they must abort the boot instead of being turned into HTTP 500s.
 */
class ConfigurationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
