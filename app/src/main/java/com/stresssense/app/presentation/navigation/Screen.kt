package com.stresssense.app.presentation.navigation

/**
 * Sealed class defining all navigation routes in StressSense.
 */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Onboarding : Screen("onboarding")
    data object Home : Screen("home")
    data object Scan : Screen("scan")
    data object History : Screen("history")
    data object Settings : Screen("settings")
    data object Questionnaire : Screen("questionnaire")
    data object PrivacyPolicy : Screen("privacy_policy")
}
