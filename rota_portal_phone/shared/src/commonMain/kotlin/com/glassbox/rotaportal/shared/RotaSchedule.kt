package com.glassbox.rotaportal.shared

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

data class ScheduleEntry(
    val id: String,
    val start: String,
    val end: String,
    val member: String,
    val applyMember: String,
    val username: String?,
    val kind: ShiftKind,
    val dayName: String,
)

data class OpsgenieOverride(
    val alias: String,
    val member: String,
    val username: String?,
    val start: String,
    val end: String,
)

data class MonthlyRota(
    val scheduleName: String,
    val rotationName: String,
    val year: Int,
    val month: Int,
    val entries: List<ScheduleEntry>,
    val overrides: List<OpsgenieOverride>,
    val teamMembers: List<String>,
    val defaultAssignableMembers: List<String>,
    val overridesError: String? = null,
)

enum class ShiftKind {
    Regular,
    Weekend,
}

object RotaSchedule {
    private const val FIRST_WORKDAY_USER = "Gabi"
    private const val FIRST_WEEKEND_USER = "Gabi"

    private val workdayRotation = listOf("Yossi", "Nadav", "Gabi", "Gour")
    private val weekendRotation = listOf("Extra1", "Extra2", "Yossi", "Gabi", "Gour")
    private val weekendRotationWhenDati = listOf("Extra1", "Extra2", "Yossi", "Gour", "Gabi")
    private val weeklyPairs = listOf(
        listOf("Gabi", "Yossi"),
        listOf("Gour", "Nadav"),
        listOf("Yossi", "Gabi"),
        listOf("Nadav", "Gour"),
    )
    private val anchorSunday = LocalDate(2026, 5, 3)
    private val holidays = setOf(
        "01/04/2026",
        "07/04/2026",
        "21/04/2026",
        "21/05/2026",
        "11/09/2026",
        "12/09/2026",
        "20/09/2026",
        "25/09/2026",
        "02/10/2026",
    )
    private val extraMemberSubstitutions = mapOf(
        "Extra1" to "Gabi",
        "Extra2" to "Gour",
    )

    fun monthEntries(year: Int, month: Int): List<ScheduleEntry> {
        val firstDay = LocalDate(year, month, 1)
        val nextMonth = if (month == 12) LocalDate(year + 1, 1, 1) else LocalDate(year, month + 1, 1)
        return createNonOverlappingSchedule(year).filter { entry ->
            val startDate = entry.start.substring(0, 10).toLocalDate()
            startDate >= firstDay && startDate < nextMonth
        }
    }

    private fun createNonOverlappingSchedule(year: Int): List<ScheduleEntry> {
        val regularRotation = workdayRotation.rotatedTo(FIRST_WORKDAY_USER).toMutableList()
        val weekend = weekendRotation.rotatedTo(FIRST_WEEKEND_USER).toMutableList()
        val weekendWhenDati = weekendRotationWhenDati.toMutableList()
        val entries = mutableListOf<RawEntry>()
        var currentDate = if (year < 2026) LocalDate(year, 1, 1) else LocalDate(2026, 1, 1)
        val endDate = LocalDate(year, 12, 31)

        while (currentDate <= endDate) {
            val day = currentDate.dayOfWeek

            if (day == DayOfWeek.FRIDAY || currentDate.isHoliday()) {
                if (day == DayOfWeek.FRIDAY) {
                    regularRotation.rotateLeft()
                }
                val assignedMember = weekend.rotateAndGetNext()

                if (entries.isNotEmpty()) {
                    val last = entries.last()
                    if (day == DayOfWeek.FRIDAY && last.member == assignedMember && entries.size >= 2) {
                        val previous = entries[entries.lastIndex - 1]
                        if (previous.kind != ShiftKind.Weekend) {
                            entries[entries.lastIndex] = last.copy(member = previous.member)
                            entries[entries.lastIndex - 1] = previous.copy(member = last.member)
                        }
                    }
                }

                if (assignedMember in setOf("Moriah", "Dovid", "Nadav")) {
                    entries += RawEntry(currentDate, currentDate, utcTime(5, 0), utcTime(12, 0), assignedMember, ShiftKind.Weekend, "Friday")
                    val shabbatMember = weekendWhenDati.rotateAndGetNext()
                    entries += RawEntry(currentDate, currentDate.plus(DatePeriod(days = 1)), utcTime(12, 0), utcTime(17, 0), shabbatMember, ShiftKind.Weekend, "Saturday")
                    entries += RawEntry(currentDate.plus(DatePeriod(days = 1)), currentDate.plus(DatePeriod(days = 2)), utcTime(17, 0), utcTime(5, 0), assignedMember, ShiftKind.Weekend, "Friday")
                } else {
                    entries += RawEntry(currentDate, currentDate.plus(DatePeriod(days = 2)), utcTime(5, 0), utcTime(5, 0), assignedMember, ShiftKind.Weekend, "Saturday")
                }

                currentDate = currentDate.plus(DatePeriod(days = 2))
                if (currentDate.dayOfWeek == DayOfWeek.SATURDAY) {
                    currentDate = currentDate.plus(DatePeriod(days = 1))
                }
            } else {
                val nextDay = currentDate.plus(DatePeriod(days = 1))
                val pair = currentDate.weekPair()
                val assignedMember = when (day) {
                    DayOfWeek.SUNDAY,
                    DayOfWeek.MONDAY -> pair[0]
                    DayOfWeek.TUESDAY,
                    DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY -> pair[1]
                    else -> regularRotation[0]
                }

                if (day in setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY)) {
                    entries += RawEntry(currentDate, currentDate, utcTime(4, 0), utcTime(17, 0), assignedMember, ShiftKind.Regular, day.displayName())
                    entries += RawEntry(currentDate, nextDay, utcTime(17, 0), utcTime(5, 0), "Dovid", ShiftKind.Regular, day.displayName())
                } else {
                    entries += RawEntry(currentDate, nextDay, utcTime(5, 0), utcTime(5, 0), assignedMember, ShiftKind.Regular, day.displayName())
                }

                currentDate = nextDay
            }
        }

        return entries.map { it.toScheduleEntry() }
    }

    private fun RawEntry.toScheduleEntry(): ScheduleEntry {
        val applyMember = extraMemberSubstitutions[member] ?: member
        val startValue = startDate.toIsoZ(startTime)
        val endValue = endDate.toIsoZ(endTime)
        val rawId = "${startValue}_${endValue}_${member}_${kind.name}".replace(":", "")
        return ScheduleEntry(
            id = rawId,
            start = startValue,
            end = endValue,
            member = member,
            applyMember = applyMember,
            username = TeamDirectory.usernameFor(applyMember),
            kind = kind,
            dayName = dayName,
        )
    }

    private data class RawEntry(
        val startDate: LocalDate,
        val endDate: LocalDate,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val member: String,
        val kind: ShiftKind,
        val dayName: String,
    )

    private fun LocalDate.weekPair(): List<String> {
        val daysSinceSunday = when (dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }
        val weekSunday = plus(DatePeriod(days = -daysSinceSunday))
        val weeksSinceAnchor = (weekSunday.toEpochDays() - anchorSunday.toEpochDays()) / 7
        return weeklyPairs[weeksSinceAnchor.toInt().floorMod(weeklyPairs.size)]
    }

    private fun LocalDate.isHoliday(): Boolean = toLegacyDate() in holidays

    private fun LocalDate.toLegacyDate(): String {
        return "${day.twoDigits()}/${(month.ordinal + 1).twoDigits()}/$year"
    }

    private fun LocalDate.toIsoZ(time: LocalTime): String {
        return "${this}T${time.hour.twoDigits()}:${time.minute.twoDigits()}:00Z"
    }

    private fun List<String>.rotatedTo(first: String): List<String> {
        val index = indexOf(first)
        return if (index <= 0) this else drop(index) + take(index)
    }

    private fun MutableList<String>.rotateLeft() {
        add(removeAt(0))
    }

    private fun MutableList<String>.rotateAndGetNext(): String {
        rotateLeft()
        return last()
    }

    private fun DayOfWeek.displayName(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')

    private fun Int.floorMod(other: Int): Int {
        val result = this % other
        return if (result < 0) result + other else result
    }

    private fun utcTime(hour: Int, minute: Int): LocalTime = LocalTime(hour, minute)

    private fun String.toLocalDate(): LocalDate {
        val parts = split("-")
        return LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }
}
