package com.therapist.assistant

import com.therapist.assistant.data.WorkshopParticipant
import com.therapist.assistant.domain.WorkshopStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopStatsTest {
    @Test
    fun summarize_improvesAndAttendance() {
        val parts = listOf(
            WorkshopParticipant(workshopId = 1, fullName = "A", preTestScore = "10", postTestScore = "15", attended = true),
            WorkshopParticipant(workshopId = 1, fullName = "B", preTestScore = "12", postTestScore = "11", attended = true),
            WorkshopParticipant(workshopId = 1, fullName = "C", preTestScore = "10", postTestScore = "10", attended = false)
        )
        val s = WorkshopStats.summarize(parts)
        assertEquals(3, s.total)
        assertEquals(2, s.attended)
        assertEquals(1, s.improvedCount)
        assertTrue(s.avgDelta != null)
    }
}
