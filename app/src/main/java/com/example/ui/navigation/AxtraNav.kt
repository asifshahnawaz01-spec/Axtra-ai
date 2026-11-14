package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.auth.GoogleAuthHelper
import com.example.auth.authStateFlow
import com.example.data.remote.CloudSyncRepository
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CommandsToolsScreen
import com.example.ui.screens.HistoryMemoryScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.KeepAliveGuideScreen
import com.example.ui.screens.LockScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PermissionHealthScreen
import com.example.ui.screens.PrivacyAboutScreen
import com.example.ui.screens.RoutinesScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.LocalAxtraPalette
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.RoutinesViewModel
import com.example.ui.viewmodel.SettingsViewModel
import com.example.ui.viewmodel.ToolsViewModel
import com.example.utils.PermissionUtils
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.serialization.Serializable

@Serializable object HomeRoute
@Serializable data class CommandsRoute(val initialTab: Int = 0)
@Serializable object RoutinesRoute
@Serializable object HistoryRoute
@Serializable object SettingsRoute
@Serializable object PermissionHealthRoute
@Serializable object KeepAliveRoute
@Serializable object PrivacyAboutRoute

private data class BottomNavTab(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun AxtraRootApp(
    mainViewModel: MainViewModel,
    permissionUtils: PermissionUtils,
    googleAuthHelper: GoogleAuthHelper,
    cloudSyncRepository: CloudSyncRepository
) {
    val palette = LocalAxtraPalette.current
    val settings by mainViewModel.settings.collectAsStateWithLifecycle()
    val auth = remember { Firebase.auth }
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    var showSplash by remember { mutableStateOf(true) }
    var isUnlocked by remember { mutableStateOf(false) }

    if (showSplash) {
        SplashScreen(onFinished = { showSplash = false })
        return
    }

    if (!settings.onboardingCompleted) {
        OnboardingScreen(
            permissionUtils = permissionUtils,
            onFinish = { mainViewModel.completeOnboarding() }
        )
        return
    }

    // Root Auth Gate: feature UI and Firestore sync require Google Sign-In
    val signedInUser = currentUser
    if (signedInUser == null) {
        AuthScreen(
            authHelper = googleAuthHelper,
            onSignedIn = {}
        )
        return
    }

    val settingsViewModel: SettingsViewModel = hiltViewModel()

    if (settings.appLockEnabled && !isUnlocked) {
        LockScreen(
            hasPinSet = settingsViewModel.hasPinSet(),
            verifyPin = { settingsViewModel.verifyPin(it) },
            onUnlocked = { isUnlocked = true }
        )
        return
    }

    LaunchedEffect(signedInUser.uid) {
        cloudSyncRepository.upsertUserProfile(
            userId = signedInUser.uid,
            displayName = signedInUser.displayName ?: "Axtra Owner",
            email = signedInUser.email,
            photoUrl = signedInUser.photoUrl?.toString(),
            preferredLanguage = settings.languageMode.name
        )
    }

    key(signedInUser.uid) {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDest = navBackStackEntry?.destination
        var commandsInitialTab by remember { mutableIntStateOf(0) }

        val tabs = remember {
            listOf(
                BottomNavTab("Home", HomeRoute, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
                BottomNavTab("Commands", CommandsRoute(0), Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_commands"),
                BottomNavTab("Routines", RoutinesRoute, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "nav_routines"),
                BottomNavTab("History", HistoryRoute, Icons.Filled.History, Icons.Outlined.History, "nav_history"),
                BottomNavTab("Settings", SettingsRoute, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
            )
        }

        val showBottomBar = currentDest?.let { dest ->
            dest.hasRoute<HomeRoute>() ||
                dest.hasRoute<CommandsRoute>() ||
                dest.hasRoute<RoutinesRoute>() ||
                dest.hasRoute<HistoryRoute>() ||
                dest.hasRoute<SettingsRoute>()
        } ?: true

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(palette.backgroundTop, palette.backgroundBottom)))
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar(
                            containerColor = palette.surfaceGlass.copy(alpha = 0.94f),
                            contentColor = palette.primaryNeon
                        ) {
                            tabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    is HomeRoute -> currentDest?.hasRoute<HomeRoute>() == true
                                    is CommandsRoute -> currentDest?.hasRoute<CommandsRoute>() == true
                                    is RoutinesRoute -> currentDest?.hasRoute<RoutinesRoute>() == true
                                    is HistoryRoute -> currentDest?.hasRoute<HistoryRoute>() == true
                                    is SettingsRoute -> currentDest?.hasRoute<SettingsRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (tab.route is CommandsRoute) commandsInitialTab = 0
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = palette.primaryNeon,
                                        selectedTextColor = palette.primaryNeon,
                                        unselectedIconColor = palette.textSecondary,
                                        unselectedTextColor = palette.textSecondary,
                                        indicatorColor = palette.primaryNeon.copy(alpha = 0.16f)
                                    ),
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    modifier = Modifier.padding(innerPadding)
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            viewModel = mainViewModel,
                            onOpenBriefing = {
                                commandsInitialTab = 1
                                navController.navigate(CommandsRoute(1))
                            }
                        )
                    }
                    composable<CommandsRoute> {
                        val toolsVm: ToolsViewModel = hiltViewModel()
                        CommandsToolsScreen(
                            viewModel = toolsVm,
                            initialTab = commandsInitialTab,
                            onRunVoiceCommand = { cmd ->
                                mainViewModel.submitCommand(cmd)
                                navController.navigate(HomeRoute) {
                                    popUpTo(HomeRoute) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable<RoutinesRoute> {
                        val routinesVm: RoutinesViewModel = hiltViewModel()
                        RoutinesScreen(viewModel = routinesVm)
                    }
                    composable<HistoryRoute> {
                        val historyVm: HistoryViewModel = hiltViewModel()
                        HistoryMemoryScreen(viewModel = historyVm)
                    }
                    composable<SettingsRoute> {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onOpenPermissionHealth = { navController.navigate(PermissionHealthRoute) },
                            onOpenKeepAliveGuide = { navController.navigate(KeepAliveRoute) },
                            onOpenPrivacyAbout = { navController.navigate(PrivacyAboutRoute) }
                        )
                    }
                    composable<PermissionHealthRoute> {
                        PermissionHealthScreen(
                            permissionUtils = permissionUtils,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<KeepAliveRoute> {
                        KeepAliveGuideScreen(
                            permissionUtils = permissionUtils,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable<PrivacyAboutRoute> {
                        PrivacyAboutScreen(
                            onDeleteAllData = {
                                settingsViewModel.deleteAllData()
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
