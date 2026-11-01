package com.glassbox.rotaportal.shared

data class OnCallPeriod(
    val id: String,
    val member: String,
    val username: String?,
    val start: String,
    val end: String,
    val type: String,
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
    val periods: List<OnCallPeriod>,
    val teamMembers: List<String>,
    val defaultAssignableMembers: List<String>,
    val timelineError: String? = null,
)

