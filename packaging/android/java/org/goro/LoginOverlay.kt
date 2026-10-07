package org.goro

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object LoginOverlay {
    @JvmStatic
    fun create(context: Context, controller: LoginController): ComposeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { LoginScreen(controller) }
        }
}

@Composable
fun LoginScreen(controller: LoginController) {
    RoTheme {
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
            if (!state.characterSelect) {
                RoButton(
                    "RO folder",
                    onClick = controller::chooseFolder,
                    enabled = !busy,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            // Scrolls and lifts above the keyboard, so a focused field is never hidden.
            Box(
                Modifier
                    .align(Alignment.Center)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            ) {
                when {
                    state.characterSelect -> CharacterSelectWindow(controller)
                    state.serverChoice -> ServerSelectWindow(controller)
                    else -> LoginWindow(
                        controller = controller,
                        username = username,
                        onUsername = { username = it },
                        password = password,
                        onPassword = { password = it },
                        keepId = keepId,
                        onKeepId = { keepId = it },
                        reveal = reveal,
                        onReveal = { reveal = !reveal },
                        busy = busy,
                        submit = submit,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerSelectWindow(controller: LoginController) {
    val state = controller.state
    RoWindow("Select Server", Modifier.widthIn(max = 380.dp).fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            state.servers.forEachIndexed { index, name ->
                RoPanel(
                    Modifier.fillMaxWidth(),
                    selected = index == state.selected,
                    onClick = { controller.selectServer(index) },
                ) {
                    Text(
                        name,
                        color = Ro.Text,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == state.selected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 13.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginWindow(
    controller: LoginController,
    username: String,
    onUsername: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    keepId: Boolean,
    onKeepId: (Boolean) -> Unit,
    reveal: Boolean,
    onReveal: () -> Unit,
    busy: Boolean,
    submit: () -> Unit,
) {
    val state = controller.state
    val serverName = state.servers.getOrElse(state.selected) { "" }
    val message = controller.error ?: when {
        state.noServer -> "No login server found. Check data/clientinfo.xml in your RO folder."
        else -> state.status
    }
    val isError = controller.error != null || state.noServer

    RoWindow(
        "Login",
        Modifier.widthIn(max = 420.dp).fillMaxWidth(),
        footer = {
            if (state.servers.size > 1) {
                RoButton("Change server", onClick = controller::changeServer, enabled = !busy)
            }
            Spacer(Modifier.weight(1f))
            RoButton(
                if (busy) "Connecting…" else "Login",
                onClick = submit,
                enabled = !busy && !state.noServer && username.isNotBlank(),
                loading = busy,
                bold = true,
            )
        },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.servers.size > 1 && serverName.isNotEmpty()) {
                Text("Server: $serverName", color = Ro.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            FieldRow("Account") {
                RoTextField(
                    value = username,
                    onValueChange = onUsername,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Next,
                    ),
                )
            }
            FieldRow("Password") {
                RoTextField(
                    value = password,
                    onValueChange = onPassword,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation('*'),
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { submit() }),
                    trailing = {
                        Text(
                            if (reveal) "Hide" else "Show",
                            color = if (busy) Ro.Muted else Ro.Label,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(enabled = !busy, onClick = onReveal)
                                .background(Ro.Selected)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    },
                )
            }
            RoCheckbox(keepId, onKeepId, "Remember account", enabled = !busy)
            if (message.isNotBlank()) {
                Text(message, color = if (isError) Ro.Error else Ro.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Ro.Label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.widthIn(min = 72.dp))
        Box(Modifier.weight(1f)) { field() }
    }
}

@Composable
private fun CharacterSelectWindow(controller: LoginController) {
    val state = controller.state
    val slot = controller.selectedSlot
    val page = if (slot < 0) 0 else slot / 3
    val pageCount = maxOf(1, (state.maxSlots + 2) / 3)
    val selected = state.characters.firstOrNull { it.slot == slot }
    val busy = state.charPending

    RoWindow(
        "Select Character",
        Modifier.widthIn(max = 700.dp).fillMaxWidth(),
        footer = {
            RoButton("Delete", onClick = { controller.character("delete") }, enabled = selected != null && !busy)
            Spacer(Modifier.weight(1f))
            RoButton("Make", onClick = { controller.character("make") }, enabled = selected == null && !busy)
            RoButton("OK", onClick = { controller.character("ok") }, enabled = selected != null && !busy, loading = busy, bold = true)
            RoButton("Cancel", onClick = { controller.character("cancel") }, enabled = !busy)
        },
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                Modifier.weight(1.2f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ArrowButton("‹", enabled = slot > 0 && !busy) { controller.selectSlot(slot - 1) }
                    repeat(3) { i ->
                        val n = page * 3 + i
                        SlotCard(
                            character = state.characters.firstOrNull { it.slot == n },
                            selected = n == slot,
                            modifier = Modifier.weight(1f),
                        ) {
                            if (!busy) {
                                if (n == slot) controller.character("activate", n) else controller.selectSlot(n)
                            }
                        }
                    }
                    ArrowButton("›", enabled = slot < state.maxSlots - 1 && !busy) { controller.selectSlot(slot + 1) }
                }
                Text("${page + 1} / $pageCount", color = Ro.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            InfoPanel(selected, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ArrowButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    RoButton(glyph, onClick = onClick, enabled = enabled, modifier = Modifier.size(width = 40.dp, height = 56.dp))
}

@Composable
private fun SlotCard(
    character: LoginCharacter?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    RoPanel(modifier.height(124.dp), selected = selected, onClick = onClick) {
        if (character == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Create", color = Ro.Muted, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Placeholder portrait until the Go sprite preview is bridged over.
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Ro.Title),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        character.name.take(1).uppercase(),
                        color = Ro.TitleText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    character.name,
                    color = Ro.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("Lv ${character.level}", color = Ro.Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun InfoPanel(character: LoginCharacter?, modifier: Modifier = Modifier) {
    RoPanel(modifier) {
        if (character == null) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Empty Slot", color = Ro.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Use Make to create a character.", color = Ro.Muted, fontSize = 13.sp)
            }
        } else {
            val left = listOf(
                "Name" to character.name,
                "Job" to character.job,
                "Level" to "${character.level} / ${character.jobLevel}",
                "Exp" to "${character.exp}",
                "HP" to "${character.hp} / ${character.maxHp}",
                "SP" to "${character.sp} / ${character.maxSp}",
            )
            val right = listOf(
                "STR" to character.str, "AGI" to character.agi, "VIT" to character.vit,
                "INT" to character.int, "DEX" to character.dex, "LUK" to character.luk,
            )
            Column(Modifier.padding(2.dp)) {
                left.forEachIndexed { i, (label, value) ->
                    // Alternating rows use characterSelect's TableColors(selected, footer).
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(if (i % 2 == 0) Ro.Selected else Ro.Footer)
                            .padding(horizontal = 6.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = Ro.Label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.widthIn(min = 42.dp))
                        Text(value, color = Ro.Text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(right[i].first, color = Ro.Label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.widthIn(min = 32.dp))
                        Text("${right[i].second}", color = Ro.Text, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 28.dp))
                    }
                }
            }
        }
    }
}
