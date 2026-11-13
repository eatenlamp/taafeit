// SPDX-FileCopyrightText: 2026 eatenlamp eatenlamp@proton.me
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package taafeit.app.presentation.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import taafeit.app.MainActivity

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Identity : Screen("identity", "Identity", Icons.Default.Person)
    object Vault : Screen("vault", "Vault", Icons.Default.Lock)
    object Shield : Screen("shield", "Shield", Icons.Default.Build)
    object Analyze : Screen("analyze", "Analyze", Icons.Default.Search)
    object Privacy : Screen("privacy", "Privacy", Icons.Default.Shield)
    object Status : Screen("status", "Status", Icons.Default.Info)
    object Config : Screen("config", "Config", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity) {
    val navController = rememberNavController()
    val screens = listOf(Screen.Identity, Screen.Vault, Screen.Shield, Screen.Analyze, Screen.Privacy, Screen.Status, Screen.Config)
    var currentRoute by remember { mutableStateOf(Screen.Identity.route) }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Taafeit", color = MaterialTheme.colorScheme.primary) }) },
        bottomBar = {
            NavigationBar(tonalElevation = 8.dp) {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null, tint = if (currentRoute == screen.route) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            currentRoute = screen.route
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = Screen.Identity.route, modifier = Modifier.padding(innerPadding)) {
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
