/**
 * True when this page is running inside an iframe - which for this app means the Chrome extension's side
 * panel (see extension/sidepanel.html), a cross-origin frame (chrome-extension://... embedding
 * http://localhost:8080). Chrome restricts some browser features for cross-origin iframe content that
 * work fine in a normal top-level tab:
 *  - Clipboard API: needs the parent to delegate it via the iframe's `allow="clipboard-write"` (now set -
 *    this was the actual cause of "copy" buttons failing with a permissions-policy error).
 *  - Dragging a file out to the OS desktop (the "DownloadURL" trick): Chrome does not support this
 *    reliably from inside a cross-origin iframe at all - this is a security boundary, not a bug in this
 *    app, and there is no iframe `allow` feature that grants it back.
 * `window.top` throws instead of just differing when the parent is cross-origin and blocks access
 * entirely, so this is wrapped defensively.
 */
export const isEmbedded: boolean = (() => {
    try {
        return window.self !== window.top;
    } catch {
        return true;
    }
})();
