package app.spliit.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import app.spliit.AppContainer
import app.spliit.ui.group.GroupScreen
import app.spliit.ui.groups.GroupsListScreen
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitTheme

private const val GROUPS = "groups"
private const val GROUP_DETAIL = "group/{groupId}"

@Composable
fun SpliitApp(container: AppContainer) {
    SpliitTheme {
        val navController = rememberNavController()

        // A push in the app is a push in SwiftUI: the new screen slides in from the trailing
        // edge on a spring while the one behind it shifts a quarter of the way and fades. The
        // pop reverses both, so the screen that was underneath is already half in place.
        val spatial = SpliitMotion.spatialSlow<IntOffset>()

        NavHost(
            navController = navController,
            startDestination = GROUPS,
            enterTransition = {
                slideInHorizontally(spatial) { width -> width } +
                    fadeIn(SpliitMotion.effectsDefault())
            },
            exitTransition = {
                slideOutHorizontally(spatial) { width -> -width / 4 } +
                    fadeOut(SpliitMotion.effectsFast())
            },
            popEnterTransition = {
                slideInHorizontally(spatial) { width -> -width / 4 } +
                    fadeIn(SpliitMotion.effectsDefault())
            },
            popExitTransition = {
                slideOutHorizontally(spatial) { width -> width } +
                    fadeOut(SpliitMotion.effectsFast())
            },
        ) {
            composable(GROUPS) {
                GroupsListScreen(
                    container = container,
                    onOpenGroup = { groupId ->
                        // The standard push, deliberately: the group screen is deeper in, not an
                        // expansion of the row.
                        navController.navigate("group/${Uri.encode(groupId)}")
                    },
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
        }
    }
}
