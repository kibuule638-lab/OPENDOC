package com.example

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.folioscan.app.export.ExportForegroundService
import com.folioscan.core.designsystem.LocalReduceMotion
import com.folioscan.core.model.CaptureMode
import com.folioscan.feature.capture.CaptureScreen
import com.folioscan.feature.detail.DetailScreen
import com.folioscan.feature.editor.EditorScreen
import com.folioscan.feature.folders.FoldersScreen
import com.folioscan.feature.home.HomeScreen
import com.folioscan.feature.search.SearchScreen
import com.folioscan.feature.settings.SettingsScreen
import com.folioscan.feature.vault.VaultScreen
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object FoldersRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data class CaptureRoute(val mode: String = CaptureMode.DOCUMENT.name, val isVault: Boolean = false)
@Serializable data class DetailRoute(val documentId: String, val openExport: Boolean = false)
@Serializable data class EditorRoute(val documentId: String, val pageIndex: Int = 0)
@Serializable data object VaultRoute

private data class TopLevelNavItem(
    val route: Any,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TOP_LEVEL_ITEMS = listOf(
    TopLevelNavItem(HomeRoute, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
    TopLevelNavItem(FoldersRoute, "Folders", Icons.Filled.Folder, Icons.Outlined.Folder, "nav_folders"),
    TopLevelNavItem(SearchRoute, "Search", Icons.Filled.Search, Icons.Outlined.Search, "nav_search"),
    TopLevelNavItem(SettingsRoute, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

@Composable
fun FolioNavShell(
    widthSizeClass: WindowWidthSizeClass,
    onSetWindowSecure: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current

    val showChrome = currentDest == null || TOP_LEVEL_ITEMS.any { item ->
        currentDest.hasRoute(item.route::class)
    }
    val useNavRail = widthSizeClass != WindowWidthSizeClass.Compact

    val navigateTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val animDuration = if (reduceMotion) 0 else 220

    Scaffold(
        bottomBar = {
            if (showChrome && !useNavRail) {
                NavigationBar {
                    TOP_LEVEL_ITEMS.forEach { item ->
                        val selected = currentDest?.hasRoute(item.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showChrome && useNavRail) {
                NavigationRail(modifier = Modifier.fillMaxHeight()) {
                    TOP_LEVEL_ITEMS.forEach { item ->
                        val selected = currentDest?.hasRoute(item.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(item.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) },
                            modifier = Modifier.testTag(item.testTag)
                        )
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                NavHost(
                    navController = navController,
                    startDestination = HomeRoute,
                    enterTransition = {
                        if (animDuration == 0) EnterTransition.None else fadeIn(tween(animDuration))
                    },
                    exitTransition = {
                        if (animDuration == 0) ExitTransition.None else fadeOut(tween(animDuration))
                    }
                ) {
                    composable<HomeRoute> {
                        HomeScreen(
                            onOpenSearch = { navigateTopLevel(SearchRoute) },
                            onOpenCapture = { mode ->
                                navController.navigate(CaptureRoute(mode = mode.name, isVault = false))
                            },
                            onOpenDocument = { docId ->
                                navController.navigate(DetailRoute(documentId = docId))
                            },
                            onOpenVault = {
                                navController.navigate(VaultRoute)
                            },
                            onOpenExportForDocument = { docId ->
                                navController.navigate(DetailRoute(documentId = docId, openExport = true))
                            }
                        )
                    }

                    composable<FoldersRoute> {
                        FoldersScreen(
                            onOpenDocument = { docId ->
                                navController.navigate(DetailRoute(documentId = docId))
                            },
                            onOpenExportForDocument = { docId ->
                                navController.navigate(DetailRoute(documentId = docId, openExport = true))
                            }
                        )
                    }

                    composable<SearchRoute> {
                        SearchScreen(
                            onOpenDocument = { docId ->
                                navController.navigate(DetailRoute(documentId = docId))
                            }
                        )
                    }

                    composable<SettingsRoute> {
                        SettingsScreen(
                            onOpenVault = { navController.navigate(VaultRoute) }
                        )
                    }

                    composable<CaptureRoute> {
                        CaptureScreen(
                            onClose = { navController.popBackStack() },
                            onOpenSettings = {
                                navController.popBackStack()
                                navigateTopLevel(SettingsRoute)
                            },
                            onScanCompleted = { newDocId ->
                                navController.navigate(DetailRoute(documentId = newDocId)) {
                                    popUpTo<CaptureRoute> { inclusive = true }
                                }
                            }
                        )
                    }

                    composable<DetailRoute> {
                        DetailScreen(
                            onBack = { navController.popBackStack() },
                            onOpenEditor = { docId, pageIdx ->
                                navController.navigate(EditorRoute(documentId = docId, pageIndex = pageIdx))
                            },
                            onSetWindowSecure = onSetWindowSecure,
                            onStartExportService = { pageCount ->
                                ExportForegroundService.start(context, pageCount)
                            }
                        )
                    }

                    composable<EditorRoute> {
                        EditorScreen(
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<VaultRoute> {
                        VaultScreen(
                            onBack = { navController.popBackStack() },
                            onOpenDocument = { docId ->
                                navController.navigate(DetailRoute(documentId = docId))
                            },
                            onScanIntoVault = { mode ->
                                navController.navigate(CaptureRoute(mode = mode.name, isVault = true))
                            },
                            onSetWindowSecure = onSetWindowSecure
                        )
                    }
                }
            }
        }
    }
}

fun Activity.applyWindowSecure(secure: Boolean) {
    if (secure) {
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
