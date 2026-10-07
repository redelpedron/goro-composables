package game

import "testing"

func TestLoginRemoteLatestSubmitWins(t *testing.T) {
	var r loginRemote
	if _, ok := r.take(); ok {
		t.Fatal("empty remote returned a submit")
	}
	r.post(loginSubmit{username: "a", password: "1"})
	r.post(loginSubmit{username: "b", password: "2", keepID: true})
	got, ok := r.take()
	if !ok || got.username != "b" || got.password != "2" || !got.keepID {
		t.Fatalf("take() = %+v, %v; want the second submit", got, ok)
	}
	if _, ok := r.take(); ok {
		t.Fatal("submit was delivered twice")
	}
}

func TestManagerSubmitLoginOutsideLogin(t *testing.T) {
	var m Manager
	if m.SubmitLogin("a", "b", false) {
		t.Fatal("SubmitLogin succeeded without a login mode")
	}
	if _, ok := m.LoginSnapshot(); ok {
		t.Fatal("LoginSnapshot reported a login mode")
	}
	lm := &LoginMode{}
	m.loginMode.Store(lm)
	if !m.SubmitLogin("a", "b", true) {
		t.Fatal("SubmitLogin failed with a login mode")
	}
	if got, ok := lm.remote.take(); !ok || got.username != "a" {
		t.Fatalf("submit not delivered to the mode: %+v, %v", got, ok)
	}
}

func TestLoginRemoteServerChoice(t *testing.T) {
	var r loginRemote
	if _, ok := r.takeServer(); ok {
		t.Fatal("empty remote returned a server choice")
	}
	r.postServer(2)
	r.postServer(-1)
	if got, ok := r.takeServer(); !ok || got != -1 {
		t.Fatalf("takeServer() = %d, %v; want -1 (latest wins)", got, ok)
	}
	r.postServer(1)
	r.drop()
	if _, ok := r.takeServer(); ok {
		t.Fatal("drop kept a stale server choice")
	}
}
