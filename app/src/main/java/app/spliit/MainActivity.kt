package app.spliit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Hello()
                }
            }
        }
    }
}

@Composable
private fun Hello() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "Spliit", style = MaterialTheme.typography.headlineMedium)
        // Touches java.time so the desugared library is exercised at build time.
        Text(text = LocalDate.of(2026, 11, 24).toString())
        // One core, one automirrored and two extended glyphs, so the dependency
        // and the audit's icon table are proven to resolve at compile time.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Icons.AutoMirrored.Filled.List,
                Icons.Filled.SwapHoriz,
                Icons.Filled.Payments,
                Icons.Filled.Chair,
            ).forEach { vector: ImageVector ->
                Icon(imageVector = vector, contentDescription = null)
            }
        }
    }
}
