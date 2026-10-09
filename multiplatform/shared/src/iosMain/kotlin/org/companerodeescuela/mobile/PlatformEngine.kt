package org.companerodeescuela.mobile
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
internal actual fun createPlatformEngine(): HttpClientEngine = Darwin.create()
