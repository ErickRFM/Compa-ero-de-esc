package org.companerodeescuela

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.companerodeescuela.core.attendance.AndroidAttendanceQrPackStore
import org.companerodeescuela.shared.contracts.AttendanceQrResponse
import org.companerodeescuela.shared.contracts.ClassOccurrenceContract
import org.companerodeescuela.shared.contracts.ClassOccurrenceStatusContract
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AttendanceQrPackStoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun selectedClassContextSurvivesProcessRecreationAndClearsWithOwner() {
        val owner = "qa-class-context:login"
        val store = AndroidAttendanceQrPackStore(context)
        val occurrence = ClassOccurrenceContract("occurrence-A", null, "course-A", "9A", "math", "Matemáticas", "QA Docente",
            "2026-10-08", "09:00", "10:00", ClassOccurrenceStatusContract.SCHEDULED)
        try {
            store.rememberOccurrence(owner, occurrence)
            val restored = AndroidAttendanceQrPackStore(context)
            assertEquals(occurrence, restored.restoreOccurrence(owner))
            assertEquals(null, restored.restoreOccurrence("qa-another-owner:login"))
            restored.clearSession("qa-class-context")
            assertEquals(null, AndroidAttendanceQrPackStore(context).restoreOccurrence(owner))
        } finally { store.clearSession("qa-class-context") }
    }

    @Test
    fun validPackSurvivesProcessRecreationOnSameBootAndIsOwnerScoped() {
        val owner = "qa-valid-teacher"
        val now = System.currentTimeMillis() / 1000
        val slots = listOf(AttendanceQrResponse("signed-qa", now, now + 25, 15))
        val store = AndroidAttendanceQrPackStore(context, { now }, { 100_000 }, { 10 })
        try {
            store.save(owner, "qa-session", slots)
            val restored = AndroidAttendanceQrPackStore(context, { now + 5 }, { 105_000 }, { 10 })
            assertEquals(slots, restored.read(owner, "qa-session"))
            assertTrue(restored.read("qa-another-owner", "qa-session").isEmpty())
        } finally { store.clearSession(owner) }
    }

    @Test
    fun clockChangeAcrossProcessRecreationInvalidatesPack() {
        val owner = "qa-clock-teacher"
        val initial = System.currentTimeMillis() / 1000
        var wall = initial
        var elapsed = 100_000L
        val store = AndroidAttendanceQrPackStore(context, { wall }, { elapsed }, { 10 })
        try {
            store.save(owner, "qa-session", listOf(AttendanceQrResponse("signed-qa", initial, initial + 25, 15)))
            wall += 30
            elapsed += 30_000
            assertTrue(store.read(owner, "qa-session").isEmpty())
            wall = initial
            assertTrue(AndroidAttendanceQrPackStore(context, { wall }, { elapsed }, { 10 })
                .read(owner, "qa-session").isEmpty())
        } finally { store.clearSession(owner) }
    }

    @Test
    fun rebootRequiresNewOnlinePack() {
        val owner = "qa-reboot-teacher"
        val now = System.currentTimeMillis() / 1000
        val store = AndroidAttendanceQrPackStore(context, { now }, { 100_000 }, { 10 })
        try {
            store.save(owner, "qa-session", listOf(AttendanceQrResponse("signed-qa", now, now + 25, 15)))
            assertTrue(AndroidAttendanceQrPackStore(context, { now }, { 101_000 }, { 11 })
                .read(owner, "qa-session").isEmpty())
        } finally { store.clearSession(owner) }
    }

    @Test
    fun expiredPackIsNotRestoredAfterProcessRecreation() {
        val owner = "qa-expired-teacher"
        val store = AndroidAttendanceQrPackStore(context)
        val now = System.currentTimeMillis() / 1000
        try {
            store.save(owner, "qa-session", listOf(AttendanceQrResponse("signed-qa", now - 40, now - 15, 15)))
            assertTrue(AndroidAttendanceQrPackStore(context).read(owner, "qa-session").isEmpty())
        } finally { store.clearSession(owner) }
    }

    @Test
    fun clearingOwnerRemovesPacksEvenWithoutRememberedSession() {
        val owner = "qa-signed-out-teacher"
        val store = AndroidAttendanceQrPackStore(context)
        val now = System.currentTimeMillis() / 1000
        try {
            store.save(owner, "qa-session-a", listOf(AttendanceQrResponse("signed-a", now, now + 25, 15)))
            store.save(owner, "qa-session-b", listOf(AttendanceQrResponse("signed-b", now, now + 25, 15)))
            store.clearSession(owner)
            val restored = AndroidAttendanceQrPackStore(context)
            assertTrue(restored.read(owner, "qa-session-a").isEmpty())
            assertTrue(restored.read(owner, "qa-session-b").isEmpty())
        } finally { store.clearSession(owner) }
    }
}
