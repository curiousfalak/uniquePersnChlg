package com.example.uniquepersnchlg



import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.uniquepersnchlg.ui.screens.AppNavHost


import com.example.uniquepersnchlg.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FaceCollageTheme {
                Surface(modifier = Modifier) {
                    AppNavHost(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun FaceCollageTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}