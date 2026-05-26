package com.focusai.app.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.focusai.app.ui.about.AboutScreen
import com.focusai.app.ui.apps.AppManagerScreen
import com.focusai.app.ui.focus.FocusScreen
import com.focusai.app.ui.navigation.MainTab
import com.focusai.app.ui.settings.SettingsScreen
import com.focusai.app.ui.stats.StatsScreen
import com.focusai.app.ui.theme.IndigoLight
import com.focusai.app.viewmodel.FocusViewModel

private const val ROUTE_APP_MANAGER = "app_manager"
private const val NAV_ANIM_DURATION = 280

@Composable
fun FocusApp() {
    val navController = rememberNavController()
    val focusViewModel: FocusViewModel = viewModel()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in MainTab.entries.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = androidx.compose.ui.unit.Dp(0f)
                ) {
                    MainTab.entries.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = {
                                Text(
                                    text = stringResource(tab.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = IndigoLight,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.Focus.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(NAV_ANIM_DURATION),
                        initialOffset = { (it * 0.08f).toInt() }
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.Start,
                        animationSpec = tween(NAV_ANIM_DURATION),
                        targetOffset = { (it * 0.08f).toInt() }
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(NAV_ANIM_DURATION)) +
                    slideIntoContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(NAV_ANIM_DURATION),
                        initialOffset = { (it * 0.08f).toInt() }
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(NAV_ANIM_DURATION)) +
                    slideOutOfContainer(
                        towards = AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(NAV_ANIM_DURATION),
                        targetOffset = { (it * 0.08f).toInt() }
                    )
            }
        ) {
            composable(MainTab.Focus.route) {
                FocusScreen(
                    viewModel = focusViewModel,
                    onNavigateAppManager = { navController.navigate(ROUTE_APP_MANAGER) }
                )
            }
            composable(MainTab.Stats.route) { StatsScreen() }
            composable(MainTab.Settings.route) { SettingsScreen() }
            composable(MainTab.About.route) { AboutScreen() }
            composable(ROUTE_APP_MANAGER) {
                AppManagerScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
