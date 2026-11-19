package io.github.rude56.poemstatus.ui.navigation3

import top.yukonga.miuix.kmp.nav.core.NavKey

sealed interface Route : NavKey {
    data object Main : Route
    data object Status : Route
    data object Appearance : Route

    data object Holes : Route

    data object HoleNew : Route

    data class HoleEdit(val slot: Int) : Route

    data object Whitelist : Route

    data object About : Route

    data class PoemEdit(val poemId: String? = null) : Route

    fun encode(): String = when (this) {
        Main -> "main"
        Status -> "status"
        Appearance -> "appearance"
        Holes -> "holes"
        HoleNew -> "holenew"
        Whitelist -> "whitelist"
        About -> "about"
        is HoleEdit -> "hole:$slot"
        is PoemEdit -> "poem:${poemId ?: ""}"
    }

    companion object {

        fun decode(s: String): Route? = when {
            s == "main" -> Main
            s == "status" -> Status
            s == "appearance" -> Appearance
            s == "holes" -> Holes
            s == "holenew" -> HoleNew
            s == "whitelist" -> Whitelist
            s == "about" -> About
            s.startsWith("hole:") -> s.removePrefix("hole:").toIntOrNull()?.let(::HoleEdit)
            s.startsWith("poem:") -> PoemEdit(s.removePrefix("poem:").ifEmpty { null })
            else -> null
        }
    }
}
