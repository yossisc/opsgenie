package com.glassbox.rotaportal.shared

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

class RotaRepository(
    private val config: RotaConfig,
    private val opsgenieClient: OpsgenieClient,
) {
    suspend fun loadMonth(year: Int, month: Int): MonthlyRota {
        val requestDate = LocalDate(year, month, 1)
            .atStartOfDayIn(TimeZone.UTC)
            .toString()
            .replace(Regex("\\.\\d+Z$"), "Z")

        val timelineResult = try {
            TimelineResult.Success(
                withTimeout(TIMELINE_FETCH_TIMEOUT_MS) {
                    opsgenieClient.getFinalTimeline(
                        scheduleName = config.scheduleName,
                        rotationName = config.rotationName,
                        date = requestDate,
                    )
                },
            )
        } catch (exc: CancellationException) {
            throw exc
        } catch (exc: Exception) {
            TimelineResult.Failure(
                exc.message?.let { "Timeline sync failed: $it" } ?: "Timeline sync failed.",
            )
        }

        val periods = when (timelineResult) {
            is TimelineResult.Success -> filterAndMergeMonth(
                periods = timelineResult.periods,
                year = year,
                month = month,
            )
            is TimelineResult.Failure -> emptyList()
        }

        return MonthlyRota(
            scheduleName = config.scheduleName,
            rotationName = config.rotationName,
            year = year,
            month = month,
            periods = periods,
            teamMembers = TeamDirectory.memberNames(),
            defaultAssignableMembers = TeamDirectory.defaultAssignableMembers(),
            timelineError = (timelineResult as? TimelineResult.Failure)?.message,
        )
    }

    suspend fun overrideShift(
        member: String,
        start: String,
        end: String,
        partial: Boolean,
    ): OverrideMutationResult {
        val username = TeamDirectory.usernameFor(member)
            ?: throw IllegalArgumentException("Unknown member: $member")
        return opsgenieClient.createOrUpdateOverride(
            scheduleName = config.scheduleName,
            rotationName = config.rotationName,
            request = OverrideMutationRequest(
                member = member,
                username = username,
                start = start,
                end = end,
                partial = partial,
            ),
        )
    }

    suspend fun overridePeriods(
        member: String,
        periods: List<OnCallPeriod>,
    ): BulkOverrideResult {
        val results = periods.map { period ->
            try {
                BulkOverrideItem(
                    entryId = period.id,
                    result = overrideShift(
                        member = member,
                        start = period.start,
                        end = period.end,
                        partial = false,
                    ),
                    error = null,
                )
            } catch (exc: Exception) {
                BulkOverrideItem(
                    entryId = period.id,
                    result = null,
                    error = exc.message ?: "Override failed.",
                )
            }
        }
        return BulkOverrideResult(results)
    }

    private fun filterAndMergeMonth(
        periods: List<OnCallPeriod>,
        year: Int,
        month: Int,
    ): List<OnCallPeriod> {
        val startBound = LocalDate(year, month, 1).atStartOfDayIn(TimeZone.UTC)
        val endBound = if (month == 12) {
            LocalDate(year + 1, 1, 1).atStartOfDayIn(TimeZone.UTC)
        } else {
            LocalDate(year, month + 1, 1).atStartOfDayIn(TimeZone.UTC)
        }

        val filtered = periods.mapNotNull { period ->
            val start = period.start.toInstantOrNull() ?: return@mapNotNull null
            val end = period.end.toInstantOrNull() ?: return@mapNotNull null
            if (end > startBound && start < endBound) period else null
        }.sortedBy { it.start.toInstantOrNull() }
        return mergeContiguousPeriods(filtered)
    }

    private fun mergeContiguousPeriods(periods: List<OnCallPeriod>): List<OnCallPeriod> {
        if (periods.isEmpty()) return emptyList()
        val merged = mutableListOf<OnCallPeriod>()
        var current = periods.first()
        for (period in periods.drop(1)) {
            val samePerson = current.member == period.member && current.username == period.username
            val currentEnd = current.end.toInstantOrNull()
            val periodStart = period.start.toInstantOrNull()
            val contiguous = currentEnd != null && currentEnd == periodStart
            if (samePerson && contiguous) {
                val type = if (current.type == "override" || period.type == "override") {
                    "override"
                } else {
                    current.type
                }
                current = current.copy(
                    end = period.end,
                    type = type,
                    id = "${current.start}_${period.end}_${current.member}_$type".replace(":", ""),
                )
            } else {
                merged += current
                current = period
            }
        }
        merged += current
        return merged
    }

    private fun String.toInstantOrNull(): Instant? {
        return try {
            Instant.parse(this)
        } catch (_: Exception) {
            null
        }
    }

    private sealed interface TimelineResult {
        data class Success(val periods: List<OnCallPeriod>) : TimelineResult
        data class Failure(val message: String) : TimelineResult
    }

    private companion object {
        const val TIMELINE_FETCH_TIMEOUT_MS = 30_000L
    }
}

data class OverrideMutationRequest(
    val member: String,
    val username: String,
    val start: String,
    val end: String,
    val partial: Boolean,
)

data class OverrideMutationResult(
    val operation: OverrideOperation,
    val alias: String,
    val override: OpsgenieOverride,
    val carvedCount: Int = 0,
)

data class BulkOverrideResult(
    val items: List<BulkOverrideItem>,
) {
    val succeeded: Int = items.count { it.result != null }
    val failed: Int = items.count { it.error != null }
}

data class BulkOverrideItem(
    val entryId: String,
    val result: OverrideMutationResult?,
    val error: String?,
)

enum class OverrideOperation {
    Created,
    Updated,
}
