package com.glassbox.rotaportal.shared

object TeamDirectory {
    private val members = linkedMapOf(
        "Yossi" to "yossi.schwartz@glassboxdigital.com",
        "Dovid" to "dovid.friedman@glassboxdigital.com",
        "Yaron" to "yaron@glassboxdigital.com",
        "Nadav" to "nadav.kosovsky@glassboxdigital.com",
        "Gabi" to "gavriel.matatov@glassboxdigital.com",
        "Eran" to "eran.meiri@glassboxdigital.com",
        "Avraham" to "avraham.aseme@glassboxdigital.com",
    )

    fun usernameFor(member: String): String? = members[member]

    fun memberNames(): List<String> = members.keys.toList()

    fun defaultAssignableMembers(): List<String> = listOf("Yossi", "Gabi", "Nadav", "Dovid", "Eran", "Avraham")

    fun memberForUsername(username: String?): String {
        return members.entries.firstOrNull { it.value == username }?.key
            ?: username
            ?: "unknown"
    }
}
