package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.auth.GoogleAuthHelper
import com.example.core.AxtraConstants
import com.example.core.configureAppCheck
import com.example.data.remote.CloudSyncRepository
import com.example.ui.navigation.AxtraRootApp
import com.example.ui.theme.AxtraTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.utils.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    @Inject
    lateinit var permissionUtils: PermissionUtils

    @Inject
    lateinit var googleAuthHelper: GoogleAuthHelper

    @Inject
    lateinit var cloudSyncRepository: CloudSyncRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent before any UI, ViewModels, or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val settings by mainViewModel.settings.collectAsStateWithLifecycle()
            AxtraTheme(
                themeStyle = settings.themeStyle,
                uiLanguage = settings.uiLanguage,
                fontScaleMultiplier = settings.fontScaleMultiplier
            ) {
                AxtraRootApp(
                    mainViewModel = mainViewModel,
                    permissionUtils = permissionUtils,
                    googleAuthHelper = googleAuthHelper,
                    cloudSyncRepository = cloudSyncRepository
                )
            }
        }

        handleShortcutIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutIntent(intent)
    }

    private fun handleShortcutIntent(intent: Intent?) {
        when (intent?.action) {
            AxtraConstants.ACTION_TALK_TO_AXTRA, Intent.ACTION_ASSIST -> {
                val initialCommand = intent.getStringExtra(AxtraConstants.EXTRA_INITIAL_COMMAND)
                if (!initialCommand.isNullOrBlank()) {
                    mainViewModel.submitCommand(initialCommand)
                } else {
                    mainViewModel.toggleMicListening()
                }
            }
            AxtraConstants.ACTION_TOGGLE_ALWAYS_ON -> {
                val cur = mainViewModel.settings.value.alwaysOnEnabled
                mainViewModel.toggleAlwaysOn(!cur)
            }
        }
    }
}
