package com.brotv.iptv

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appStartsAndRendersComposeRoot() {
        composeRule.onRoot().fetchSemanticsNode()
    }

    @Test
    fun freshInstallShowsLoginChoices() {
        composeRule.onNodeWithText("Xtream").fetchSemanticsNode()
        composeRule.onNodeWithText("M3U URL").fetchSemanticsNode()
        composeRule.onNodeWithText("تسجيل الدخول").fetchSemanticsNode()
    }
}
