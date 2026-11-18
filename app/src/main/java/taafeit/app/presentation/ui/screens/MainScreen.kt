// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import taafeit.app.MainActivity
import taafeit.app.R

sealed class Screen(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val primary: Boolean = false,
) {
    data object Identity : Screen("identity", R.string.tab_spoofing, Icons.Default.Person, true)
    data object Vault : Screen("vault", R.string.tab_profiles, Icons.Default.Lock, true)
    data object Shield : Screen("shield", R.string.tab_automation, Icons.Default.Shield, true)
    data object Analyze : Screen("analyze", R.string.tab_scan, Icons.Default.Search, true)
    data object Privacy : Screen("privacy", R.string.tab_non_root, Icons.Default.Security, true)
    data object Status : Screen("status", R.string.tab_info, Icons.Default.Info)
    data object Config : Screen("config", R.string.tab_settings, Icons.Default.Settings)
}

val allScreens = listOf(
    Screen.Identity,
    Screen.Vault,
    Screen.Shield,
    Screen.Analyze,
    Screen.Privacy,
    Screen.Status,
    Screen.Config,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.Identity.route
    val currentScreen = allScreens.firstOrNull { it.route == currentRoute }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(currentScreen?.titleRes ?: R.string.app_name),
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    if (currentScreen != null && !currentScreen.primary) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.close),
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { navController.openSingleTop(Screen.Status.route) }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.tab_info))
                    }
                    IconButton(onClick = { navController.openSingleTop(Screen.Config.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.tab_settings))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(tonalElevation = 8.dp) {
                allScreens.filter { it.primary }.forEach { screen ->
                    val selected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = selected,
                        alwaysShowLabel = false,
                        onClick = { navController.openTab(screen.route) },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = stringResource(screen.titleRes),
                                tint = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        },
                        label = { Text(stringResource(screen.titleRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Identity.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Screen.Identity.route) { IdentityScreen(activity) }
            composable(Screen.Vault.route) { VaultScreen(activity) }
            composable(Screen.Shield.route) { ShieldScreen(activity) }
            composable(Screen.Analyze.route) { AnalyzeScreen(activity) }
            composable(Screen.Privacy.route) { PrivacyScreen(activity) }
            composable(Screen.Status.route) { StatusScreen(activity) }
            composable(Screen.Config.route) { ConfigScreen(activity) }
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.openSingleTop(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}
