package org.goro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

data class LoginUiState(
    val active: Boolean = false,
    val credentials: Boolean = false,
    val pending: Boolean = false,
    val status: String = "",
    val username: String = "",
    val keepId: Boolean = false,
    val noServer: Boolean = false,
    val handled: Long = 0,
    val serverChoice: Boolean = false,
    val servers: List<String> = emptyList(),
    val selected: Int = 0,
)

/**
 * Bridges the Go login mode and the Compose form. The Activity feeds it the
 * JSON from nativeLoginState on the main thread; the screen reads [state].
 */
class LoginController(private val actions: Actions) {
    interface Actions {
        fun submit(username: String, password: String, keepId: Boolean): Boolean
        fun server(index: Int): Boolean
        fun chooseFolder()
    }

    var state by mutableStateOf(LoginUiState())
        private set

    /** True from a tap until the game thread reports it has processed it. */
    var submitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var sent = 0L

    /** Returns whether the form should be on screen. */
    fun update(json: String): Boolean {
        val next = try {
            val o = JSONObject(json)
            LoginUiState(
                active = o.optBoolean("active"),
                credentials = o.optBoolean("credentials"),
                pending = o.optBoolean("pending"),
                status = o.optString("status"),
                username = o.optString("username"),
                keepId = o.optBoolean("keepId"),
                noServer = o.optBoolean("noServer"),
                handled = o.optLong("handled"),
                serverChoice = o.optBoolean("serverChoice"),
                servers = o.optJSONArray("servers")?.let { a -> List(a.length()) { a.optString(it) } } ?: emptyList(),
                selected = o.optInt("selected"),
            )
        } catch (_: Exception) {
            return state.active && (state.credentials || state.serverChoice)
        }
        state = next
        val visible = next.active && (next.credentials || next.serverChoice)
        if (!visible || next.handled >= sent) submitting = false
        return visible
    }

    fun submit(username: String, password: String, keepId: Boolean) {
        if (submitting || state.pending) return
        error = null
        if (actions.submit(username.trim(), password, keepId)) {
            sent += 1
            submitting = true
        } else {
            error = "The game is not ready yet. Try again in a moment."
        }
    }

    /** Picks a login server; the form for it appears once Go has moved on. */
    fun selectServer(index: Int) {
        if (!actions.server(index)) error = "The game is not ready yet. Try again in a moment."
    }

    /** Returns to the server list from the account form. */
    fun changeServer() {
        if (!submitting && !state.pending) actions.server(-1)
    }

    fun chooseFolder() = actions.chooseFolder()
}
