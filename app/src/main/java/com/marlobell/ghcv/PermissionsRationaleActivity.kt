package com.marlobell.ghcv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marlobell.ghcv.ui.theme.GhcvTheme

/**
 * Activity to show the rationale/privacy policy when user clicks
 * the privacy policy link in Health Connect permission screen.
 * 
 * Required by Health Connect for Android 14+
 */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GhcvTheme {
                PermissionsRationaleScreen(
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun PermissionsRationaleScreen(onClose: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Privacy Policy & Data Usage",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = "GHCV (Google Health Connect Visualizer) reads your health data to provide visualizations and insights.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Why we need permissions:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Read your health data to display charts and statistics\n" +
                               "• Visualize trends over time\n" +
                               "• Compare daily metrics",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Your data privacy:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• All data stays on your device\n" +
                               "• No data is sent to external servers\n" +
                               "• No data is shared with third parties\n" +
                               "• Read-only access - we never modify your data",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close")
            }
        }
    }
}
