package com.glassbox.rotaportal.shared

import kotlinx.coroutines.CancellationException

class RotaRepository(
    private val config: RotaConfig,
    private val opsgenieClient: OpsgenieClient,
) {
    suspend fun loadMonth(year: Int, month: Int): MonthlyRota {
        val entries = RotaSchedule.monthEntries(year, month)
        val overridesResult = try {
            OverridesResult.Success(opsgenieClient.listOverrides(config.scheduleName))
        } catch (exc: CancellationException) {
            throw exc
        } catch (exc: Exception) {
            OverridesResult.Failure("Override sync failed.")
        }

        return MonthlyRota(
            scheduleName = config.scheduleName,
            rotationName = config.rotationName,
            year = year,
            month = month,
            entries = entries,
            overrides = when (overridesResult) {
                is OverridesResult.Success -> overridesResult.overrides
                is OverridesResult.Failure -> emptyList()
            },
            teamMembers = TeamDirectory.memberNames(),
            defaultAssignableMembers = TeamDirectory.defaultAssignableMembers(),
            overridesError = (overridesResult as? OverridesResult.Failure)?.message,
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

    suspend fun overrideShifts(
        member: String,
        shifts: List<ScheduleEntry>,
    ): BulkOverrideResult {
        val results = shifts.map { shift ->
            try {
                BulkOverrideItem(
                    entryId = shift.id,
                    result = overrideShift(
                        member = member,
                        start = shift.start,
                        end = shift.end,
                        partial = false,
                    ),
                    error = null,
                )
            } catch (exc: Exception) {
                BulkOverrideItem(
                    entryId = shift.id,
                    result = null,
                    error = exc.message ?: "Override failed.",
                )
            }
        }
        return BulkOverrideResult(results)
    }

    private sealed interface OverridesResult {
        data class Success(val overrides: List<OpsgenieOverride>) : OverridesResult
        data class Failure(val message: String) : OverridesResult
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
