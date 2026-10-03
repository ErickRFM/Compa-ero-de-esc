package org.companerodeescuela.core.network

import javax.inject.Qualifier

/** Qualifies the platform HTTP client shared by repositories and workers. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlatformApiClient
