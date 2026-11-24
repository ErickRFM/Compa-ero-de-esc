package org.companerodeescuela.feature.admin

import org.companerodeescuela.core.ui.component.NoticeTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class InvitationDeliveryPresentationTest {
    @Test
    fun `provider acceptance is never presented as mailbox delivery`() {
        val (acceptedTone, acceptedTitle, acceptedDetail) = invitationDeliveryPresentation("ACCEPTED")
        assertNotEquals(NoticeTone.SUCCESS, acceptedTone)
        assertEquals(NoticeTone.INFO, acceptedTone)
        assertTrue(acceptedTitle.contains("entrega pendiente", ignoreCase = true))
        assertTrue(acceptedDetail.contains("aún no confirma", ignoreCase = true))

        val (deliveredTone, deliveredTitle, _) = invitationDeliveryPresentation("DELIVERED")
        assertEquals(NoticeTone.SUCCESS, deliveredTone)
        assertTrue(deliveredTitle.contains("entregado", ignoreCase = true))
    }

    @Test
    fun `bounced failed delayed and pending statuses have distinct tones and labels`() {
        assertEquals(NoticeTone.ERROR, invitationDeliveryPresentation("BOUNCED").first)
        assertTrue(invitationDeliveryPresentation("BOUNCED").second.contains("rebotado", ignoreCase = true))

        assertEquals(NoticeTone.ERROR, invitationDeliveryPresentation("FAILED").first)
        assertEquals(NoticeTone.ERROR, invitationDeliveryPresentation("SUPPRESSED").first)

        assertEquals(NoticeTone.WARNING, invitationDeliveryPresentation("DELAYED").first)
        assertEquals(NoticeTone.WARNING, invitationDeliveryPresentation("PENDING").first)
        assertEquals(NoticeTone.WARNING, invitationDeliveryPresentation("UNCONFIRMED").first)
    }
}
