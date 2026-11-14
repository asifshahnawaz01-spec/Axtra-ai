package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.OnboardingScreen
import com.example.ui.theme.AxtraTheme
import com.example.utils.PermissionUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `app name matches Axtra AI`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Axtra AI", appName)
    }

    @Test
    fun `onboarding flow renders permission cards and advances`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val permissionUtils = PermissionUtils(context)
        var finished = false

        composeTestRule.setContent {
            AxtraTheme {
                OnboardingScreen(
                    permissionUtils = permissionUtils,
                    onFinish = { finished = true }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_screen").assertIsDisplayed()
        composeTestRule.onNodeWithText("Microphone").assertIsDisplayed()

        // Advance to second permission card
        composeTestRule.onNodeWithTag("onboarding_next_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Accessibility Service").assertIsDisplayed()

        // Skip entire wizard
        composeTestRule.onNodeWithTag("onboarding_back_skip_btn").performClick() // Back to 1st
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("onboarding_back_skip_btn").performClick() // Skip Wizard
        composeTestRule.waitForIdle()
        assertTrue(finished)
    }

    @Test
    fun `voice interaction service is registered in manifest`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = android.content.Intent(android.service.voice.VoiceInteractionService.SERVICE_INTERFACE)
            .setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentServices(
            intent,
            android.content.pm.PackageManager.GET_META_DATA
        )
        assertTrue(resolved.any { it.serviceInfo.name.endsWith("AxtraVoiceInteractionService") })
    }
}
