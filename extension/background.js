// Owns the whole "send this job to JTracker" job, but there's very little of it now: capture the page as
// a PDF and POST it. The app itself accepts the PDF instantly (POST /api/jobs/intake returns before
// anything reads it) and does the actual (slow) reading later, entirely on its own - so there is nothing
// long-running left for this extension to survive. Any ambiguous-duplicate decision is resolved in the
// JTracker app itself, not here.

const API_BASE = "http://localhost:8080";

/** @type {{ phase: "idle"|"running"|"done"|"error", message?: string } } */
let state = { phase: "idle" };

function setState(next) {
    state = next;
    // No listener (e.g. popup closed) throws "Could not establish connection" - that's expected, ignore it.
    chrome.runtime.sendMessage({ type: "state", state }).catch(() => {});
}

chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message.type === "getState") {
        sendResponse(state);
        return;
    }
    if (message.type === "start") {
        if (state.phase === "running") {
            sendResponse(state); // already in progress - don't start a second, duplicate attempt
            return;
        }
        startJob();
        sendResponse(state);
        return;
    }
});

async function startJob() {
    setState({ phase: "running", message: "Checking JTracker is running…" });
    try {
        await checkServerIsUp();

        setState({ phase: "running", message: "Capturing this page…" });
        const tab = await getActiveTab();
        const pdfBlob = await capturePageAsPdf(tab.id);

        setState({ phase: "running", message: "Sending to JTracker…" });
        const { status, body } = await postIntake(pdfBlob, tab.url);

        // 202: newly queued, or the same URL was already queued - either way, nothing more to do here.
        // 200: the URL was already turned into a job - the app never re-read it.
        if (status !== 202 && status !== 200) {
            throw new Error((body && body.error) || `JTracker returned an error (HTTP ${status}).`);
        }
        const message = body && body.alreadyTracked
            ? "Already tracked — check the app for status."
            : "Sent to JTracker — check the app for status.";
        setState({ phase: "done", message });
        notify("JTracker", message);
    } catch (err) {
        const message = err.message || String(err);
        setState({ phase: "error", message });
        notify("JTracker - could not send this job", message);
    }
}

async function checkServerIsUp() {
    let response;
    try {
        response = await fetch(`${API_BASE}/api/jobs`, { signal: AbortSignal.timeout(3000) });
    } catch {
        throw new Error("JTracker isn't running — start the app (localhost:8080) and try again.");
    }
    if (!response.ok) {
        throw new Error("JTracker isn't running — start the app (localhost:8080) and try again.");
    }
}

function getActiveTab() {
    return new Promise((resolve, reject) => {
        chrome.tabs.query({ active: true, lastFocusedWindow: true }, ([tab]) => {
            if (chrome.runtime.lastError || !tab) {
                reject(new Error("Could not find the active tab."));
            } else {
                resolve(tab);
            }
        });
    });
}

function capturePageAsPdf(tabId) {
    return new Promise((resolve, reject) => {
        chrome.debugger.attach({ tabId }, "1.3", () => {
            if (chrome.runtime.lastError) {
                reject(new Error("Could not read this page: " + chrome.runtime.lastError.message));
                return;
            }
            chrome.debugger.sendCommand({ tabId }, "Page.printToPDF", { printBackground: true }, (result) => {
                const error = chrome.runtime.lastError;
                chrome.debugger.detach({ tabId }, () => {
                    if (error) {
                        reject(new Error("Could not save this page as a PDF: " + error.message));
                    } else {
                        resolve(base64ToBlob(result.data, "application/pdf"));
                    }
                });
            });
        });
    });
}

function base64ToBlob(base64, type) {
    const bytes = Uint8Array.from(atob(base64), (c) => c.charCodeAt(0));
    return new Blob([bytes], { type });
}

async function postIntake(pdfBlob, url) {
    const form = new FormData();
    form.append("jdPdf", pdfBlob, "job.pdf");
    if (url) {
        form.append("url", url);
    }
    let response;
    try {
        response = await fetch(`${API_BASE}/api/jobs/intake`, { method: "POST", body: form });
    } catch {
        throw new Error("JTracker isn't running — start the app (localhost:8080) and try again.");
    }
    const body = await response.json().catch(() => null);
    return { status: response.status, body };
}

function notify(title, message) {
    chrome.notifications.create(`jtracker-${Date.now()}`, {
        type: "basic",
        iconUrl: "icon128.png",
        title,
        message,
    });
}
