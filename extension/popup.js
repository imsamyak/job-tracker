// A thin viewer only - all the actual work (server check, PDF capture, upload) happens in background.js
// so it survives this popup being closed. Closing this popup never stops or restarts anything; reopening
// it just shows whatever background.js's state currently is. There's no confirm/decline UI here anymore -
// an ambiguous duplicate is resolved in the JTracker app itself, not in the extension.

const statusEl = document.getElementById("status");
const retryBtn = document.getElementById("retry");

const onFailure = (err) => render({ phase: "error", message: err.message });
retryBtn.addEventListener("click", () => send({ type: "start" }).then(render).catch(onFailure));

send({ type: "getState" })
    .then((state) => {
        if (state.phase === "idle") {
            return send({ type: "start" }).then(render);
        }
        render(state); // something's already running or just finished - show it, don't start a duplicate
    })
    .catch((err) => {
        // Most likely: background.js hasn't started or errored on load. Check chrome://extensions ->
        // this extension -> "service worker" console for the real cause; this at least surfaces that
        // something is wrong instead of leaving the popup stuck on its static "Starting…" text forever.
        render({ phase: "error", message: "Could not reach the extension's background worker: " + err.message });
    });

// Live updates while this popup happens to be open (harmless no-op once it closes).
chrome.runtime.onMessage.addListener((message) => {
    if (message.type === "state") {
        render(message.state);
    }
});

/** chrome.runtime.sendMessage rejects if there's no background listener (e.g. it crashed on load). */
function send(message) {
    return chrome.runtime.sendMessage(message).catch((err) => {
        throw new Error(err && err.message ? err.message : String(err));
    });
}

function render(state) {
    retryBtn.hidden = state.phase !== "error";
    const kind = { running: "loading", done: "ok", error: "error" }[state.phase] || "loading";
    setStatus(kind, state.message || "Starting…");
}

function setStatus(kind, text) {
    statusEl.className = kind === "loading" ? "" : kind;
    statusEl.innerHTML = kind === "loading" ? `<span class="spinner"></span><span></span>` : `<span></span>`;
    statusEl.lastElementChild.textContent = text;
}
