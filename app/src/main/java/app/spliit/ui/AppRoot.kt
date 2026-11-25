package app.spliit.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.spliit.AppContainer
import app.spliit.ui.group.GroupScreen
import app.spliit.ui.groups.GroupsListScreen
import app.spliit.ui.settings.SettingsScreen
import app.spliit.ui.theme.SpliitTheme

private const val GROUPS = "groups"
private const val GROUP_DETAIL = "group/{groupId}"
private const val SETTINGS = "settings"

@Composable
fun SpliitApp(container: AppContainer) {
    SpliitTheme {
        val navController = rememberNavController()

        NavHost(navController = navController, startDestination = GROUPS) {
            composable(GROUPS) {
                GroupsListScreen(
                    container = container,
                    onOpenGroup = { groupId ->
                        // The standard push, deliberately: the group screen is deeper in, not an
                        // expansion of the row.
                        navController.navigate("group/${Uri.encode(groupId)}")
                    },
                    onOpenSettings = { navController.navigate(SETTINGS) },
                )
            }

            composable(
                route = GROUP_DETAIL,
                arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
            ) { entry ->
                GroupScreen(
                    container = container,
                    groupId = entry.arguments?.getString("groupId").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }

            composable(SETTINGS) {
                SettingsScreen(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
