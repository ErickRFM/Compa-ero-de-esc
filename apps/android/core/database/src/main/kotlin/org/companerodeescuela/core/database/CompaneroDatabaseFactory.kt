package org.companerodeescuela.core.database

import android.content.Context
import androidx.room.Room

/**
 * Owns the Room implementation detail inside core:database.
 *
 * App-level DI asks this module for a database instance; it does not need a
 * direct dependency on Room itself.
 */
object CompaneroDatabaseFactory {
    fun create(context: Context): CompaneroDatabase =
        Room.databaseBuilder(
            context.applicationContext,
            CompaneroDatabase::class.java,
            "companero-cache.db",
        ).build()
}
