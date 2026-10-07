package org.goro

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

data class LoginCharacter(
    val slot: Int,
    val name: String,
    val job: String,
    val level: Int,
    val jobLevel: Int,
    val exp: Long,
    val hp: Int,
    val maxHp: Int,
    val sp: Int,
    val maxSp: Int,
    val str: Int,
    val agi: Int,
    val vit: Int,
    val int: Int,
    val dex: Int,
    val luk: Int,
)

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
    val characterSelect: Boolean = false,
    val characters: List<LoginCharacter> = emptyList(),
    val selectedSlot: Int = 0,
    val maxSlots: Int = 9,
    val charPending: Boolean = false,
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
        fun character(kind: String, slot: Int): Boolean
    }

    var state by mutableStateOf(LoginUiState())
        private set

    /** True from a tap until the game thread reports it has processed it. */
    var submitting by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    private var sent = 0L

    // Applied at once on tap; Go confirms it in the next snapshot, which clears it.
    private var slotOverride by mutableStateOf<Int?>(null)

    val selectedSlot: Int get() = slotOverride ?: state.selectedSlot

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
                characterSelect = o.optBoolean("characterSelect"),
                characters = o.optJSONArray("characters")?.let { a ->
                    List(a.length()) { i ->
                        val c = a.getJSONObject(i)
                        LoginCharacter(
                            slot = c.optInt("slot"), name = c.optString("name"), job = c.optString("job"),
                            level = c.optInt("level"), jobLevel = c.optInt("jobLevel"), exp = c.optLong("exp"),
                            hp = c.optInt("hp"), maxHp = c.optInt("maxHp"), sp = c.optInt("sp"), maxSp = c.optInt("maxSp"),
                            str = c.optInt("str"), agi = c.optInt("agi"), vit = c.optInt("vit"),
                            int = c.optInt("int"), dex = c.optInt("dex"), luk = c.optInt("luk"),
                        )
                    }
                } ?: emptyList(),
                selectedSlot = o.optInt("selectedSlot"),
                maxSlots = o.optInt("maxSlots", 9),
                charPending = o.optBoolean("charPending"),
            )
        } catch (_: Exception) {
            return state.active && (state.credentials || state.serverChoice || state.characterSelect)
        }
        state = next
        if (slotOverride == next.selectedSlot) slotOverride = null
        val visible = next.active && (next.credentials || next.serverChoice || next.characterSelect)
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

    fun selectSlot(slot: Int) {
        val clamped = slot.coerceIn(0, (state.maxSlots - 1).coerceAtLeast(0))
        slotOverride = clamped
        if (!actions.character("select", clamped)) slotOverride = null
    }

    /** kind: activate (tap a selected slot), ok, make, delete or cancel. */
    fun character(kind: String, slot: Int = selectedSlot) {
        if (state.charPending && kind != "cancel") return
        if (!actions.character(kind, slot)) error = "The game is not ready yet. Try again in a moment."
    }
}
