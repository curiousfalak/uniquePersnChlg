package com.example.uniquepersnchlg.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.uniquepersnchlg.data.ProcessingState
import com.example.uniquepersnchlg.viewmodel.MainViewModel

private object Routes {
    const val SPLASH = "splash"
    const val PICKER = "picker"
    const val PROCESSING = "processing"
    const val RESULT = "result"
}

@Composable
fun AppNavHost(viewModel: MainViewModel) {
    val navController: NavHostController = rememberNavController()
    val state by viewModel.state.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = { fadeIn(tween(250)) + slideInHorizontally(tween(300)) { it / 6 } },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(300)) { it / 6 } }
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.PICKER) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.PICKER) {
            PickerScreen(
                onVideoChosen = { uri, label ->
                    viewModel.onVideoSelected(uri, label)
                    viewModel.startProcessing()
                    navController.navigate(Routes.PROCESSING)
                }
            )
        }
        composable(Routes.PROCESSING) {
            ProcessingScreen(
                state = state,
                onDone = { navController.navigate(Routes.RESULT) },
                onError = { navController.popBackStack(Routes.PICKER, inclusive = false) }
            )
        }
        composable(Routes.RESULT) {
            val done = state as? ProcessingState.Done
            if (done != null) {
                ResultScreen(
                    result = done.result,
                    onProcessAnother = {
                        viewModel.reset()
                        navController.popBackStack(Routes.PICKER, inclusive = false)
                    }
                )
            }
        }
    }
}
