package com.chromian.trackman

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.chromian.trackman.ui.screens.VehicleDetectionScreen
import com.chromian.trackman.ui.theme.TrackManTheme
import com.chromian.trackman.viewModel.VehicleStatusViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel = remember { VehicleStatusViewModel() }

            MaterialTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        VehicleDetectionScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
