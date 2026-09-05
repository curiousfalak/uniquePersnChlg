package com.example.uniquepersnchlg.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.facecollage.ui.PickerScreen
import com.example.facecollage.ui.ProcessingScreen
import com.example.uniquepersnchlg.data.ProcessingState
import com.example.uniquepersnchlg.ui.ResultScreen
import com.example.uniquepersnchlg.viewmodel.MainViewModel

private object Routes {
    const val PICKER = "picker"
    const val PROCESSING = "processing"
    const val RESULT = "result"
}

@Composable
fun AppNavHost(viewModel: MainViewModel) {
    val navController: NavHostController = rememberNavController()
    val state by viewModel.state.collectAsState()

    NavHost(navController = navController, startDestination = Routes.PICKER) {
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