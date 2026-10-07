package org.goro

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

object LoginOverlay {
    @JvmStatic
    fun create(context: Context, controller: LoginController): ComposeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { LoginScreen(controller) }
        }
}

private val GoroColors = darkColorScheme(
    background = Color(0xFF181C22),
    surface = Color(0xFF181C22),
    primary = Color(0xFF7FB2FF),
)

@Composable
fun LoginScreen(controller: LoginController) {
    MaterialTheme(colorScheme = GoroColors) {
        val state = controller.state
        // Held outside any visibility gate so hiding the form (a Go modal is
        // up) does not lose what the player typed.
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var keepId by remember { mutableStateOf(false) }
        var reveal by remember { mutableStateOf(false) }
        LaunchedEffect(state.username, state.keepId) {
            if (username.isEmpty()) username = state.username
            keepId = state.keepId
        }
        val focus = LocalFocusManager.current
        val busy = controller.submitting || state.pending
        val submit = {
            focus.clearFocus()
            controller.submit(username, password, keepId)
        }

        // No opaque fill: the Go-rendered login wallpaper must stay visible.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TextButton(onClick = controller::chooseFolder, enabled = !busy, modifier = Modifier.align(Alignment.TopStart)) {
                Text("RO folder")
            }
            // Scrolls and lifts above the keyboard, so a focused field is never hidden.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .imePadding()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (state.serverChoice) {
                    Panel {
                        Text("Choose a server", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        state.servers.forEachIndexed { index, name ->
                            val pick = { controller.selectServer(index) }
                            if (index == state.selected) {
                                Button(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text(name) }
                            } else {
                                FilledTonalButton(onClick = pick, modifier = Modifier.fillMaxWidth()) { Text(name) }
                            }
                        }
                    }
                } else {
                    Panel {
                        Text("Goro", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        if (state.servers.size > 1) {
                            val name = state.servers.getOrElse(state.selected) { "" }
                            TextButton(onClick = controller::changeServer, enabled = !busy) { Text("Server: $name  ·  Change") }
                        }
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text("Account") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password") },
                            singleLine = true,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "Hide" else "Show") } },
                            keyboardOptions = KeyboardOptions(
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Go,
                            ),
                            keyboardActions = KeyboardActions(onGo = { submit() }),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = keepId, onCheckedChange = { keepId = it }, enabled = !busy)
                            Text("Remember account")
                        }
                        val message = controller.error ?: when {
                            state.noServer -> "No login server found. Check data/clientinfo.xml in your RO folder."
                            else -> state.status
                        }
                        if (message.isNotBlank()) {
                            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f))
                        }
                        Button(
                            onClick = submit,
                            enabled = !busy && !state.noServer && username.isNotBlank(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.padding(end = 8.dp).size(18.dp), strokeWidth = 2.dp)
                                Text("Connecting…")
                            } else {
                                Text("Login")
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Translucent card; the Surface also supplies the readable content colour. */
@Composable
private fun Panel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().padding(16.dp),
        color = Color(0xD9181C22),
        contentColor = MaterialTheme.colorScheme.onBackground,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
