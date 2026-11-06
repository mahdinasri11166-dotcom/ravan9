package com.therapist.assistant.domain

import com.therapist.assistant.data.WorkshopParticipant

data class WorkshopStatSummary(
    val total: Int,
    val attended: Int,
    val certificates: Int,
    val avgPre: Double?,
    val avgPost: Double?,
    val avgDelta: Double?,
    val improvedCount: Int,
    val improvedPercent: Double
)

object WorkshopStats {
    fun summarize(parts: List<WorkshopParticipant>): WorkshopStatSummary {
        val pre = parts.mapNotNull { it.preTestScore.trim().toDoubleOrNull() }
        val post = parts.mapNotNull { it.postTestScore.trim().toDoubleOrNull() }
        val paired = parts.mapNotNull { p ->
            val a = p.preTestScore.trim().toDoubleOrNull()
            val b = p.postTestScore.trim().toDoubleOrNull()
            if (a != null && b != null) a to b else null
        }
        val improved = paired.count { it.second > it.first }
        return WorkshopStatSummary(
            total = parts.size,
            attended = parts.count { it.attended },
            certificates = parts.count { it.certificateIssued },
            avgPre = pre.takeIf { it.isNotEmpty() }?.average(),
            avgPost = post.takeIf { it.isNotEmpty() }?.average(),
            avgDelta = paired.takeIf { it.isNotEmpty() }?.map { it.second - it.first }?.average(),
            improvedCount = improved,
            improvedPercent = if (paired.isEmpty()) 0.0 else improved * 100.0 / paired.size
        )
    }
}
