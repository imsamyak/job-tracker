# "Apply for me" - plan (not started)

## Goal
Open a job application page, click **Apply for me** in the extension side panel. JTracker fills the form
(logging in first if needed), pauses and notifies when it needs the person, and stops **just before the final
Submit**. The person reviews and submits by hand.

## Decisions already made
- Target sites: **Workday** and **Naukri** first (Workday is the hard one; Naukri is mostly single-page).
- Credentials: stored by the app, **local only** (never synced, never in the repo), per site host.
- Free-text questions: Claude answers them itself; the person is **notified before it moves to the next page**
  and does a **full review at the end**.
- Learns from interventions: answers the person types are saved and reused next time.
- Several tabs may run at once: one session per tab, default concurrency 1, cap ~3.

## Hard rules (never relax)
1. Never click Submit / Apply / Send / Finish. Stop at the last step and hand over.
2. Never guess EEO/demographic, work-authorization or salary answers - use a saved answer or ask.
3. Never capture or store passwords/OTPs/CAPTCHA input into the answers memory.
4. Every Claude-written answer must come from the real profile (`CandidateProfile`) + the job's JD.
5. Any surprise (unknown page, login failure, CAPTCHA, OTP, email verification) = pause + notify, don't push on.

## Architecture
- **Extension** (has `debugger`, `activeTab`, `notifications`, `sidePanel` already): content script/CDP reads a
  page snapshot (fields, labels, types, dropdown options, buttons) and executes actions (fill, select, click,
  upload). Side panel per tab: Apply button, status, inbox of pauses.
- **Backend**: `ApplySession` per tab (job match by URL, state: FILLING / PAUSED / READY_FOR_REVIEW / DONE).
  Step loop: snapshot -> deterministic fill from profile/answers memory -> Claude only for what's left ->
  actions back to the extension. Reuse the `claude -p` writer pattern (interface + Claude impl + fake for tests).
- **Stores** (H2, editable from the UI): `Profile` (structured facts), `SiteCredential` (host, email, password),
  `SavedAnswer` (question, normalized key, type, options, answer, remember flag, last used).
- **File upload**: extension fetches the tailored PDF from localhost and assigns it to the file input via
  `DataTransfer`.

## Phases
**Phase 0 - Data foundations**
- Profile fields screen (extends the Links modal idea): notice period, current/expected CTC, work authorization,
  relocation, years of experience, etc.
- Credentials screen. SavedAnswer table + "Your answers" screen (view/edit/delete).

**Phase 1 - Fill-and-stop, Naukri**
- Page snapshot + action executor. Deterministic mapping from profile. Resume/cover letter attach.
- Submit guard. Highlight everything that was filled. Side-panel "Apply for me" button.
- Done when: a real Naukri apply page is filled and stops before Submit with nothing clicked wrongly.

**Phase 2 - Step loop, pauses, sessions**
- Multi-step loop with Claude for the leftover fields; free-text answers with notify-before-next-page.
- Chrome notifications with tab + job name; side-panel inbox; Continue button resumes.
- Per-tab sessions, concurrency setting.

**Phase 3 - Workday**
- Login/signup (uses stored credential), email-verification pause, multi-page flow, shadow DOM, custom
  dropdowns/date pickers, "My Information / Experience / Questions / Review" pages.
- Done when: a full Workday application reaches the Review page unattended except for declared pauses.

**Phase 4 - Learning + polish**
- Capture what the person types on paused fields ("remember this?" toggle), Claude semantic matching of
  reworded questions, dropdown option mapping. Dedupe the same question across tabs. Failure logs.

## Cost / limits control
- Deterministic first, Claude only for leftovers; saved answers cut calls further over time.
- Keep Haiku for field mapping; consider a different provider or an API key for this automation only
  (see the provider-abstraction note below) so it doesn't eat the Pro plan's weekly limit.
- Provider abstraction: every writer is already an interface (`CoverLetterWriter`, `JobHelpWriter`, fit
  calculator) - a new `Apply` writer should follow the same shape so the backend model/provider can be swapped.

## Risks
- Workday quirks (shadow DOM, iframes, custom widgets) - plan extra time.
- Background tabs get throttled - CDP focus emulation helps but isn't perfect.
- Site terms of service on automation - person reviews and submits, which keeps this low risk.
- Debugger banner / DevTools conflicts on controlled tabs.

## Open questions for later
- Naukri: which flows (Easy Apply vs company-site redirects)?
- Where should credentials live: backend DB vs `chrome.storage.local`?
- Do we want a dry-run mode that records what it *would* fill without touching the page?
