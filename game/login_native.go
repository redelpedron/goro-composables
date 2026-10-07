package game

import (
	"sync"
	"sync/atomic"
	"time"

	"github.com/kivutar/goro/client"
	"github.com/kivutar/goro/db"
)

// nativeLogin is set by hosts (Android) that draw their own credential form.
// The Go login window is then never created; the host drives LoginMode through
// Manager.SubmitLogin and reads progress through Manager.LoginSnapshot.
var nativeLogin atomic.Bool

func SetNativeLogin(on bool) { nativeLogin.Store(on) }

// LoginSnapshot is copied out of the game thread once per Update. The password
// is deliberately absent.
type LoginSnapshot struct {
	Phase    string `json:"phase"`
	Status   string `json:"status"`
	Pending  bool   `json:"pending"`
	Username string `json:"username"`
	KeepID   bool   `json:"keepId"`

	// Credentials is true only while the account form is the right thing to
	// show. It is false during server selection, the character-service picker
	// and Go-drawn modals (connection failed, quit), which the host must not
	// cover.
	Credentials bool `json:"credentials"`

	// ServerChoice is true while the host should show the server list instead
	// of the account form. Servers holds display names, Selected the current one.
	ServerChoice bool     `json:"serverChoice"`
	Servers      []string `json:"servers"`
	Selected     int      `json:"selected"`

	// Handled counts submits the game thread has finished processing. Dialing
	// blocks Update for up to 5s, so Pending alone cannot tell the host that a
	// tap was received; the host compares Handled with how many it sent.
	Handled uint64 `json:"handled"`

	// NoServer is true when clientinfo.xml yielded no login server, so a
	// submit can never succeed and the host should say so instead of spinning.
	NoServer bool `json:"noServer"`

	// CharacterSelect is true while the host should draw the character list.
	// It is false while a Go modal (delete confirm, disconnect, quit) is up.
	CharacterSelect bool             `json:"characterSelect"`
	Characters      []LoginCharacter `json:"characters"`
	SelectedSlot    int              `json:"selectedSlot"`
	MaxSlots        int              `json:"maxSlots"`
	CharPending     bool             `json:"charPending"`
}

// LoginCharacter is the subset of session.Character the native list shows.
type LoginCharacter struct {
	Slot     int    `json:"slot"`
	Name     string `json:"name"`
	Job      string `json:"job"`
	Level    int    `json:"level"`
	JobLevel int    `json:"jobLevel"`
	Exp      int64  `json:"exp"`
	HP       int    `json:"hp"`
	MaxHP    int    `json:"maxHp"`
	SP       int    `json:"sp"`
	MaxSP    int    `json:"maxSp"`
	Str      int    `json:"str"`
	Agi      int    `json:"agi"`
	Vit      int    `json:"vit"`
	Int      int    `json:"int"`
	Dex      int    `json:"dex"`
	Luk      int    `json:"luk"`
}

// loginCharAction is a character-select tap: select, activate, ok, make,
// delete or cancel.
type loginCharAction struct {
	kind string
	slot int
}

type loginSubmit struct {
	username, password string
	keepID             bool
}

// loginRemote hands data between the host UI thread and the game thread.
// The latest submit wins; a second tap before the game thread runs is not
// queued twice.
type loginRemote struct {
	mu     sync.Mutex
	submit  *loginSubmit
	server  *int
	chars   []loginCharAction
	snap    LoginSnapshot
	handled uint64
}

func (r *loginRemote) post(s loginSubmit) {
	r.mu.Lock()
	r.submit = &s
	r.mu.Unlock()
}

func (r *loginRemote) take() (loginSubmit, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.submit == nil {
		return loginSubmit{}, false
	}
	s := *r.submit
	r.submit = nil
	return s, true
}

// drop discards a submit that was never consumed, so a tap made while a modal
// was up cannot fire later.
func (r *loginRemote) drop() {
	r.mu.Lock()
	r.submit = nil
	r.server = nil
	r.chars = nil
	r.mu.Unlock()
}

// postChar queues a character-select action. Unlike submits, every action
// matters (select then ok), so they queue in order with a small cap.
func (r *loginRemote) postChar(a loginCharAction) {
	r.mu.Lock()
	if len(r.chars) < 8 {
		r.chars = append(r.chars, a)
	}
	r.mu.Unlock()
}

func (r *loginRemote) takeChars() []loginCharAction {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := r.chars
	r.chars = nil
	return out
}

// postServer records a server pick (index >= 0) or a request to go back to
// the server list (-1). The latest request wins.
func (r *loginRemote) postServer(index int) {
	r.mu.Lock()
	r.server = &index
	r.mu.Unlock()
}

func (r *loginRemote) takeServer() (int, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.server == nil {
		return 0, false
	}
	index := *r.server
	r.server = nil
	return index, true
}

func (r *loginRemote) markHandled() {
	r.mu.Lock()
	r.handled++
	r.mu.Unlock()
}

func (r *loginRemote) publish(s LoginSnapshot) {
	r.mu.Lock()
	s.Handled = r.handled
	r.snap = s
	r.mu.Unlock()
}

func (r *loginRemote) snapshot() LoginSnapshot {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.snap
}

// updateNativeLogin replaces the Go login window on the account step. It runs
// on the game thread, so it may call connectAndMaybeLogin like OnSubmit did.
func (m *LoginMode) updateNativeLogin(ctx client.Context) {
	if index, ok := m.remote.takeServer(); ok && index < 0 {
		m.showLoginServerSelection(ctx)
		return
	}
	s, ok := m.remote.take()
	if !ok {
		return
	}
	defer m.remote.markHandled()
	m.username, m.password, m.keepID = s.username, s.password, s.keepID
	m.saveLoginID(ctx)
	conn, found := m.selectedLoginConnection(ctx)
	if !found {
		m.status = "no login servers discovered"
		return
	}
	m.connectAndMaybeLogin(ctx, conn, true)
}

// updateNativeServerChoice replaces the Go server window. selectLoginServer
// advances to the credentials step exactly as the window's OnSelect did.
func (m *LoginMode) updateNativeServerChoice(ctx client.Context) {
	if index, ok := m.remote.takeServer(); ok && index >= 0 {
		m.selectLoginServer(ctx, index)
	}
}

func (m *LoginMode) publishRemote(ctx client.Context) {
	phase := "account"
	switch m.phase {
	case loginPhaseCharacter:
		phase = "character"
	case loginPhaseCreate:
		phase = "create"
	}
	credentials := m.phase == loginPhaseAccount &&
		m.accountStep == loginAccountCredentials &&
		m.serviceWindow == nil &&
		!m.disconnectDialog.IsOpen() && !m.quitConfirm.IsOpen()
	conns := loginConnections(ctx)
	picking := m.phase == loginPhaseAccount &&
		m.accountStep == loginAccountConnection &&
		len(conns) > 1 && !ctx.Config.Login.AutoLogin &&
		m.serviceWindow == nil &&
		!m.disconnectDialog.IsOpen() && !m.quitConfirm.IsOpen()
	modalOpen := m.disconnectDialog.IsOpen() || m.quitConfirm.IsOpen() ||
		m.charDeleteConfirm.IsOpen() || m.charDeletePrompt.IsOpen()
	characterSelect := m.phase == loginPhaseCharacter && m.serviceWindow == nil && !modalOpen
	if !credentials && !picking && !characterSelect {
		m.remote.drop()
	}
	m.remote.publish(LoginSnapshot{
		Phase:        phase,
		Status:       m.status,
		Pending:      m.loginPending,
		Username:     m.username,
		KeepID:       m.keepID,
		Credentials:  credentials,
		ServerChoice: picking,
		Servers:      loginServerNames(conns),
		Selected:     m.selectedLoginServer,
		NoServer:     len(conns) == 0,

		CharacterSelect: characterSelect,
		Characters:      loginCharacters(ctx),
		SelectedSlot:    m.selectedSlot,
		MaxSlots:        m.maxSlots,
		CharPending:     m.charSelectPending,
	})
}

// updateNativeCharacterSelect applies host taps with the same behavior as the
// Go window's callbacks. It runs on the game thread.
func (m *LoginMode) updateNativeCharacterSelect(ctx client.Context) {
	for _, a := range m.remote.takeChars() {
		if ctx.Session == nil {
			return
		}
		switch a.kind {
		case "select":
			m.selectedSlot = clampCharacterSlot(a.slot, m.maxSlots)
		case "activate":
			m.activateCharacterSelectSlot(ctx, a.slot, time.Now())
		case "ok":
			m.submitSelectedCharacter(ctx)
		case "make":
			if _, ok := characterBySlot(ctx.Session.Characters, m.selectedSlot); ok {
				m.status = "character slot occupied"
				continue
			}
			m.openCharacterCreate(ctx, m.selectedSlot, time.Now())
		case "delete":
			m.openCharacterDeleteConfirm(ctx)
		case "cancel":
			m.cancelCharacterSelect(ctx)
			return
		}
	}
}

func loginCharacters(ctx client.Context) []LoginCharacter {
	if ctx.Session == nil {
		return nil
	}
	out := make([]LoginCharacter, 0, len(ctx.Session.Characters))
	for _, c := range ctx.Session.Characters {
		out = append(out, LoginCharacter{
			Slot: int(c.Slot), Name: c.Name, Job: db.JobDisplayName(int(c.Job)),
			Level: int(c.Level), JobLevel: int(c.JobLevel), Exp: c.Exp,
			HP: int(c.HP), MaxHP: int(c.MaxHP), SP: int(c.SP), MaxSP: int(c.MaxSP),
			Str: int(c.Str), Agi: int(c.Agi), Vit: int(c.Vit),
			Int: int(c.Int), Dex: int(c.Dex), Luk: int(c.Luk),
		})
	}
	return out
}
