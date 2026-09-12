package com.brotv.iptv

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
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
        composeRule.onRoot().assertExists()
    }

    @Test
    fun freshInstallShowsLoginChoices() {
        composeRule.onNodeWithText("Xtream").assertExists()
        composeRule.onNodeWithText("M3U URL").assertExists()
        composeRule.onNodeWithText("تسجيل الدخول").assertExists()
    }
}
