// Just embeds the real app (localhost:8080) full-height in the side panel, so the person never has to
// switch tabs to check on their applications. A plain fetch health-check decides whether to show the
// iframe or an "isn't running" message - an iframe's own error event doesn't reliably fire for a refused
// connection, so this is the dependable way to tell the two states apart.

const APP_URL = "http://localhost:8080";

const frame = document.getElementById("app");
const offline = document.getElementById("offline");
const retryBtn = document.getElementById("retry");
const refreshBtn = document.getElementById("refresh");
const saveBtn = document.getElementById("save");
const statusStrip = document.getElementById("status-strip");

let pollTimer = null;

async function isServerUp() {
    try {
        const response = await fetch(`${APP_URL}/api/jobs`, { cache: "no-store" });
        return response.ok;
    } catch {
        return false;
    }
}

async function load() {
    const up = await isServerUp();
    if (up) {
        stopPolling();
        offline.classList.remove("visible");
        // Reassigning src (even to the same value) forces a real reload, unlike frame.contentWindow.location.
        frame.src = `${APP_URL}/?t=${Date.now()}`;
    } else {
        offline.classList.add("visible");
        startPolling();
    }
}

/** While offline, keep checking in the background so the panel recovers on its own once the app starts. */
function startPolling() {
    if (pollTimer) return;
    pollTimer = setInterval(async () => {
        if (await isServerUp()) load();
    }, 4000);
}

function stopPolling() {
    clearInterval(pollTimer);
    pollTimer = null;
}

retryBtn.addEventListener("click", load);
refreshBtn.addEventListener("click", load);

load();

// --- "Save this page" - the same capture-and-send job the extension's popup triggers, available here
// too so nothing needs clicking outside the side panel. Reuses background.js's existing getState/start/
// state messaging protocol as-is (it already tracks a single in-flight job and broadcasts updates), rather
// than duplicating any of the PDF-capture logic here.

let hideDoneTimer = null;

saveBtn.addEventListener("click", () => {
    send({ type: "start" }).then(renderSaveState).catch(onSendFailure);
});

send({ type: "getState" }).then(renderSaveState).catch(() => {}); // reflect a job already in progress

chrome.runtime.onMessage.addListener((message) => {
    if (message.type === "state") {
        renderSaveState(message.state);
    }
});

function send(message) {
    return chrome.runtime.sendMessage(message).catch((err) => {
        throw new Error(err && err.message ? err.message : String(err));
    });
}

function onSendFailure(err) {
    renderSaveState({ phase: "error", message: err.message });
}

function renderSaveState(state) {
    clearTimeout(hideDoneTimer);
    saveBtn.disabled = state.phase === "running";
    saveBtn.classList.toggle("is-done", state.phase === "done");
    saveBtn.classList.toggle("is-error", state.phase === "error");
    saveBtn.innerHTML = state.phase === "running" ? '<span class="spinner"></span>' : SAVE_ICON;

    if (state.phase === "idle") {
        statusStrip.classList.remove("visible", "error", "done");
        return;
    }
    statusStrip.textContent = state.message || "";
    statusStrip.classList.add("visible");
    statusStrip.classList.toggle("error", state.phase === "error");
    statusStrip.classList.toggle("done", state.phase === "done");

    if (state.phase === "done") {
        // Auto-clear on success after a few seconds; an error stays until the next Save attempt.
        hideDoneTimer = setTimeout(() => statusStrip.classList.remove("visible", "done"), 4000);
        load(); // the job was just saved - refresh the list behind it right away
    }
}

const SAVE_ICON = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
     stroke-linecap="round" stroke-linejoin="round">
    <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2Z" />
    <path d="M17 21v-8H7v8" />
    <path d="M7 3v5h8" />
</svg>`;
