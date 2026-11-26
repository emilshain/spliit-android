package app.spliit.ui

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import app.spliit.AppContainer
import app.spliit.data.local.AppearancePalette
import app.spliit.data.local.ThemeChoice
import app.spliit.ui.group.GroupScreen
import app.spliit.ui.groups.GroupsListScreen
import app.spliit.ui.theme.SpliitMotion
import app.spliit.ui.theme.SpliitTheme

private const val GROUPS = "groups"
private const val GROUP_DETAIL = "group/{groupId}"

@Composable
fun SpliitApp(container: AppContainer) {
    val choice by container.appearance.theme.collectAsState(initial = ThemeChoice.System)
    val dynamicColor by container.appearance.dynamicColor.collectAsState(initial = true)
    val palette by container.appearance.palette.collectAsState(initial = AppearancePalette.Spliit)
    val dark = when (choice) {
        ThemeChoice.System -> isSystemInDarkTheme()
        ThemeChoice.Light -> false
        ThemeChoice.Dark -> true
    }

    SpliitTheme(dark = dark, dynamicColor = dynamicColor, palette = palette) {
        val background = MaterialTheme.colorScheme.background
        val context = LocalContext.current

        // The window paints the first frame, and every frame between two screens while one is
        // sliding, before Compose has drawn anything. Left at the platform's light default it
        // flashes white behind a dark app — so it is given the page's own colour, and the bars
        // are told which way the app reads.
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }

        val navController = rememberNavController()

        // A push in SwiftUI is a push: the new screen slides in from the trailing edge on a
        // spring while the one behind it shifts a quarter of the way. Nothing fades, so no
        // frame ever shows the window between the two screens.
        val spatial = SpliitMotion.spatialSlow<IntOffset>()

        NavHost(
            navController = navController,
            startDestination = GROUPS,
            modifier = Modifier.fillMaxSize().background(background),
            enterTransition = {
                slideInHorizontally(spatial) { width -> width }
            },
            exitTransition = {
                slideOutHorizontally(spatial) { width -> -width / 4 }
            },
            popEnterTransition = {
                slideInHorizontally(spatial) { width -> -width / 4 }
            },
            popExitTransition = {
                slideOutHorizontally(spatial) { width -> width }
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
