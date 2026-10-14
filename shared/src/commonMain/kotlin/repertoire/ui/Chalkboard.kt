package repertoire.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val CHALK = Color(0xFF121212)
private val CHALK_EDGE = Color(0xFF4A4A4A)

/** Black board with white writing: the opening small, the variant name big and bold. */
@Composable
fun Chalkboard(opening: String, name: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth()
            .background(CHALK, RoundedCornerShape(10.dp))
            .border(3.dp, CHALK_EDGE, RoundedCornerShape(10.dp))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        if (opening.isNotBlank()) {
            Text(opening, color = Color(0xFFCFCFCF), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
        }
        Text(name, color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}
