package repertoire.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * The value [by] steps on from [value] in [steps]: the next larger one, or the next smaller one.
 * A typed value between two steps goes to its neighbour, not back to the first step.
 */
fun stepIn(steps: List<Int>, value: Int, by: Int): Int = when {
    by > 0 -> steps.firstOrNull { it > value } ?: steps.last()
    by < 0 -> steps.lastOrNull { it < value } ?: steps.first()
    else -> value
}

/**
 * − [field] +: a number that can be stepped with the buttons or typed. What is typed counts as
 * soon as it lies in [range]; Enter or leaving the field puts anything outside back into it.
 * [zeroLabel]: 0 is shown as an empty field with this text in it (e.g. "unbegrenzt"), and an
 * empty field means 0.
 */
@Composable
fun NumberStepper(
    vm: AppViewModel,
    value: Int,
    range: IntRange,
    onValue: (Int) -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    unit: String? = null,
    zeroLabel: String? = null,
    fieldWidth: Int = 96,
) {
    val shown = if (value == 0 && zeroLabel != null) "" else value.toString()
    // Starts over whenever the value changes from outside, e.g. by the buttons.
    var text by remember(value) { mutableStateOf(shown) }
    fun parsed(): Int? = if (text.isBlank() && zeroLabel != null) 0 else text.trim().toIntOrNull()
    fun commit() {
        val n = parsed()?.coerceIn(range) ?: value
        if (n != value) onValue(n)
        text = if (n == 0 && zeroLabel != null) "" else n.toString()
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onMinus, enabled = value > range.first) { Text("−") }
        OutlinedTextField(
            value = text,
            onValueChange = { typed ->
                if (typed.length <= 6 && typed.all(Char::isDigit)) {
                    text = typed
                    parsed()?.takeIf { it in range && it != value }?.let(onValue)
                }
            },
            singleLine = true,
            placeholder = zeroLabel?.let { { Text(it, style = MaterialTheme.typography.bodySmall) } },
            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            modifier = Modifier.padding(horizontal = 8.dp).width(fieldWidth.dp).typing(vm)
                .onFocusChanged { if (!it.isFocused) commit() },
        )
        OutlinedButton(onClick = onPlus, enabled = value < range.last) { Text("+") }
        if (unit != null) Text(unit, modifier = Modifier.padding(start = 8.dp))
    }
}
