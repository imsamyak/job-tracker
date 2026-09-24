export type JobStatus = "NEW" | "RESUME_READY" | "FAILED";

export type CoverLetterStatus = "NONE" | "PENDING" | "READY" | "FAILED";

export type FitStatus = "PENDING" | "READY" | "FAILED";

export interface JobSummary {
    id: string;
    company: string;
    role: string;
    status: JobStatus;
    folderName: string;
    lastError: string | null;
    createdAt: string;
    applied: boolean;
    appliedAt: string | null;
    location: string | null;
    coverLetterStatus: CoverLetterStatus;
    coverLetterError: string | null;
    /** Computed automatically (not on click, unlike the cover letter) shortly after the job is saved. */
    fitStatus: FitStatus;
    fitPercentage: number | null;
    fitSummary: string | null;
    /** The "referral" resume/cover letter - same content, a fixed different email, for when a referrer is
     *  asking to pass the application along. Both are lazy, same shape as the (non-referral) cover letter.
     *  Live here (not just JobDetail) so the referral modal's doc rows update via the normal SSE refresh. */
    referralResumeStatus: CoverLetterStatus;
    referralResumeError: string | null;
    referralCoverLetterStatus: CoverLetterStatus;
    referralCoverLetterError: string | null;
}

export interface JobDetail extends JobSummary {
    /** The company's own posting id - null when the posting had none. */
    jobId: string | null;
    url: string | null;
    type: string | null;
    mode: string | null;
    skills: string[];
    /** The final resume skills (no Concepts / AI tools groups) - what the UI shows for "Skills". */
    resumeSkills: string[];
    stacks: string[];
    contacts: string[];
}

// Built and served by the backend itself (see vite.config.ts), so in production this is same-origin -
// window.location.origin, not "". `npm run dev` still talks across ports, so that mode defaults to
// localhost:8080. Must be a fully-qualified absolute URL (not ""/relative): the "drag a file out of the
// browser" DownloadURL trick (FilesModal, ReferralNoteModal) silently does nothing with a relative URL -
// Chrome resolves that string itself when writing the dropped file, without the page's own base URL to
// resolve it against. This was the actual cause of "drag and drop not working" - every fetch() call below
// worked fine with a relative "" base (the browser resolves those against the current page), but the drag
// trick never got a chance to.
const API_BASE = import.meta.env.VITE_API_BASE ?? (import.meta.env.DEV ? "http://localhost:8080" : window.location.origin);

export async function fetchJobs(): Promise<JobSummary[]> {
    const response = await fetch(`${API_BASE}/api/jobs`);
    if (!response.ok) {
        throw new Error(`Could not load applications (HTTP ${response.status})`);
    }
    return response.json();
}

/**
 * Removes the job record. By default the generated resume/JD files are left on disk - pass
 * deleteFolder: true (e.g. from a checkbox the person ticked) to remove that folder too.
 */
export async function deleteJob(id: string, deleteFolder: boolean): Promise<void> {
    const url = `${API_BASE}/api/jobs/${id}?deleteFolder=${deleteFolder}`;
    const response = await fetch(url, { method: "DELETE" });
    if (!response.ok && response.status !== 404) {
        throw new Error(`Could not delete this application (HTTP ${response.status})`);
    }
}

/** Full detail for the row-click modal - everything the list view leaves out. */
export async function fetchJobDetail(id: string): Promise<JobDetail> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}`);
    if (!response.ok) {
        throw new Error(`Could not load this application (HTTP ${response.status})`);
    }
    return response.json();
}

/** Marks (or unmarks, applied: false) that the person has actually applied to this job. */
export async function setJobApplied(id: string, applied: boolean): Promise<JobSummary> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/applied?applied=${applied}`, { method: "PUT" });
    if (!response.ok) {
        throw new Error(`Could not update this application (HTTP ${response.status})`);
    }
    return response.json();
}

/** Fills in (or clears, passing "") the location by hand - mainly for postings the extractor found none for. */
export async function setJobLocation(id: string, location: string): Promise<JobSummary> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/location?location=${encodeURIComponent(location)}`, {
        method: "PUT",
    });
    if (!response.ok) {
        throw new Error(`Could not update the location (HTTP ${response.status})`);
    }
    return response.json();
}

/** Corrects (or clears, passing "") the company's posting id by hand - the extractor sometimes gets this wrong. */
export async function setJobId(id: string, jobId: string): Promise<JobSummary> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/job-id?jobId=${encodeURIComponent(jobId)}`, {
        method: "PUT",
    });
    if (!response.ok) {
        throw new Error(`Could not update the job id (HTTP ${response.status})`);
    }
    return response.json();
}

/**
 * Kicks off writing (claude -p) and rendering a tailored cover letter for this job - unlike the resume,
 * this is NEVER automatic, only ever called from an explicit "Generate" click. Returns immediately (202)
 * with the job already marked PENDING; the actual result arrives via the usual jobs-changed SSE refetch.
 */
export async function generateCoverLetter(id: string): Promise<JobSummary> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/cover-letter`, { method: "POST" });
    if (!response.ok) {
        throw new Error(`Could not start the cover letter (HTTP ${response.status})`);
    }
    return response.json();
}

/** Kicks off the "referral" resume (same content, the fixed referral email) - lazy, same shape as generateCoverLetter. */
export async function generateReferralResume(id: string): Promise<JobSummary> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/referral/resume`, { method: "POST" });
    if (!response.ok) {
        throw new Error(`Could not start the referral resume (HTTP ${response.status})`);
    }
    return response.json();
}

/** Kicks off the "referral" cover letter - same shape as generateReferralResume. */
export async function generateReferralCoverLetter(id: string): Promise<JobSummary> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/referral/cover-letter`, { method: "POST" });
    if (!response.ok) {
        throw new Error(`Could not start the referral cover letter (HTTP ${response.status})`);
    }
    return response.json();
}

/**
 * Asks Claude to answer one free-form question about this job application on the candidate's behalf -
 * e.g. something pasted straight out of the company's own application form. Synchronous (~5-30s): unlike
 * the cover letter/referral docs there's no PENDING/READY status to poll, the answer just comes back.
 */
export async function askJobHelp(id: string, question: string): Promise<string> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/help?question=${encodeURIComponent(question)}`, {
        method: "POST",
    });
    if (!response.ok) {
        throw new Error(`Could not get an answer (HTTP ${response.status})`);
    }
    const body: { answer: string } = await response.json();
    return body.answer;
}

/** The job's generated folder's absolute path, as plain text - for a "copy folder path" button. */
export async function fetchFolderPath(id: string): Promise<string> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/folder-path`);
    if (!response.ok) {
        throw new Error(`Could not load the folder path (HTTP ${response.status})`);
    }
    return response.text();
}

/** Same file as jobFileUrl but served inline, so a new tab shows the PDF in the browser's viewer instead of downloading it. */
export function jobFileViewUrl(id: string, filename: string): string {
    return `${API_BASE}/api/jobs/${id}/view/${encodeURIComponent(filename)}`;
}

export function referralResumeViewUrl(id: string): string {
    return `${API_BASE}/api/jobs/${id}/referral/resume/view`;
}

export function referralCoverLetterViewUrl(id: string): string {
    return `${API_BASE}/api/jobs/${id}/referral/cover-letter/view`;
}

export function referralResumeUrl(id: string): string {
    return `${API_BASE}/api/jobs/${id}/referral/resume`;
}

export function referralCoverLetterUrl(id: string): string {
    return `${API_BASE}/api/jobs/${id}/referral/cover-letter`;
}

/** Opens the job's generated folder (resume + job description PDF) in the OS file explorer. */
export async function openJobFolder(id: string): Promise<void> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/open-folder`, { method: "POST" });
    if (!response.ok) {
        throw new Error(`Could not open the folder (HTTP ${response.status})`);
    }
}

export interface JobFile {
    name: string;
    sizeBytes: number;
}

/** What's actually on disk in the job's generated folder - for the files/drag-out modal. */
export async function fetchJobFiles(id: string): Promise<JobFile[]> {
    const response = await fetch(`${API_BASE}/api/jobs/${id}/files`);
    if (!response.ok) {
        throw new Error(`Could not load files (HTTP ${response.status})`);
    }
    return response.json();
}

/** A real, fetchable URL for one file - used for both a plain download link and the "drag out of the
 *  browser" DownloadURL trick, which needs an actual HTTP URL behind it. */
export function jobFileUrl(id: string, filename: string): string {
    return `${API_BASE}/api/jobs/${id}/files/${encodeURIComponent(filename)}`;
}

/**
 * Asks the server to shut down after a grace period - it stays up if anything else (even just
 * clicking Refresh) uses the app before that period ends.
 */
export async function requestShutdown(): Promise<{ scheduledInSeconds: number }> {
    const response = await fetch(`${API_BASE}/api/shutdown`, { method: "POST" });
    if (!response.ok) {
        throw new Error(`Could not schedule shutdown (HTTP ${response.status})`);
    }
    return response.json();
}

export type PendingIntakeStatus = "PENDING" | "NEEDS_CONFIRMATION" | "FAILED";

export interface SimilarJob {
    id: string;
    company: string;
    role: string;
    status: JobStatus;
}

export interface PendingIntakeSummary {
    id: string;
    status: PendingIntakeStatus;
    /** null while still PENDING (not read yet). */
    company: string | null;
    role: string | null;
    /** The earlier job this one resembles - only set for NEEDS_CONFIRMATION. */
    similarJob: SimilarJob | null;
    /** Only set for FAILED. */
    errorMessage: string | null;
    createdAt: string;
    /** True only while a worker thread is reading THIS posting right now, false if it's still queued. */
    active: boolean;
}

export async function fetchPendingIntakes(): Promise<PendingIntakeSummary[]> {
    const response = await fetch(`${API_BASE}/api/pending-intakes`);
    if (!response.ok) {
        throw new Error(`Could not load pending postings (HTTP ${response.status})`);
    }
    return response.json();
}

/** Saves an ambiguous posting as a genuinely new job - the PDF is not re-read. */
export async function confirmPendingIntake(id: string): Promise<void> {
    const response = await fetch(`${API_BASE}/api/jobs/pending/${id}/confirm`, { method: "POST" });
    if (!response.ok) {
        throw new Error(`Could not save this posting (HTTP ${response.status})`);
    }
}

/** Discards a pending posting - declining an ambiguous one, or dismissing a failed one. */
export async function rejectPendingIntake(id: string): Promise<void> {
    const response = await fetch(`${API_BASE}/api/jobs/pending/${id}`, { method: "DELETE" });
    if (!response.ok && response.status !== 404) {
        throw new Error(`Could not dismiss this posting (HTTP ${response.status})`);
    }
}

/**
 * Live updates over Server-Sent Events: the server pushes a plain "something changed" signal whenever a
 * job is created, its status changes, or it's deleted - call onChange (e.g. refetch the list) each time.
 * One-way (server to browser), so SSE rather than a WebSocket; EventSource reconnects on its own if the
 * connection drops, so no reconnect logic is needed here.
 *
 * @returns a cleanup function - call it (e.g. from a useEffect cleanup) to close the connection
 */
export function subscribeToJobEvents(onChange: () => void): () => void {
    const source = new EventSource(`${API_BASE}/api/jobs/stream`);
    source.addEventListener("jobs-changed", onChange);
    return () => source.close();
}
