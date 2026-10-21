package org.companerodeescuela.api.institutions

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import kotlinx.coroutines.flow.firstOrNull
import org.bson.Document

/**
 * Opt-in, single-purpose registration of a pilot destination in Mongo.
 * Registration remains CLOSED; no users or permissions are created.
 * Never edits, disables or relabels an existing institutional record.
 */
data class PilotInstitutionSpec(
    val tenantId: String,
    val displayName: String,
)

object SiiiPilotInstitutionBootstrap {
    fun parse(env: (String) -> String?): PilotInstitutionSpec? {
        val enabled = env("SIII_PILOT_TENANT_BOOTSTRAP_ENABLED")?.trim() ?: "false"
        require(enabled in setOf("true", "false")) {
            "SIII_PILOT_TENANT_BOOTSTRAP_ENABLED must be true or false"
        }
        if (enabled == "false") return null

        fun required(name: String): String =
            env(name)?.takeIf { it.isNotBlank() && it == it.trim() }
                ?: throw IllegalArgumentException("$name is required for pilot tenant bootstrap")

        val id = required("SIII_PILOT_TENANT_ID")
        val displayName = required("SIII_PILOT_TENANT_DISPLAY_NAME")
        require(id.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) {
            "SIII_PILOT_TENANT_ID is invalid"
        }
        require(displayName.length in 2..128 && displayName.none { it.isISOControl() }) {
            "SIII_PILOT_TENANT_DISPLAY_NAME is invalid"
        }
        return PilotInstitutionSpec(id, displayName)
    }

    suspend fun provision(
        database: MongoDatabase,
        desired: PilotInstitutionSpec,
    ) {
        val collection = database.getCollection<Document>("platform_institutions")
        val existing = collection.find(eq("_id", desired.tenantId)).firstOrNull()
        if (existing != null) {
            require(existing.getString("displayName") == desired.displayName) {
                "Pilot tenant already exists with different metadata; refusing changes"
            }
            return
        }
        // Mongo _id uniqueness fails closed on concurrent conflicting provisioning.
        collection.insertOne(
            Document("_id", desired.tenantId)
                .append("displayName", desired.displayName)
                .append("registrationEnabled", false),
        )
    }
}
