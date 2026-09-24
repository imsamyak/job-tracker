import { useEffect, useRef, useState } from "react";
import {
    deleteJob,
    fetchJobs,
    fetchPendingIntakes,
    generateCoverLetter,
    generateReferralCoverLetter,
    generateReferralResume,
    openJobFolder,
    requestShutdown,
    setJobApplied,
    setJobLocation,
    subscribeToJobEvents,
    type JobSummary,
    type PendingIntakeSummary,
} from "./api";
import { AppliedBadge, FitBadge, StatusBadge } from "./StatusBadge";
import { ConfirmDialog } from "./ConfirmDialog";
import { PendingIntakes } from "./PendingIntakes";
import { JobDetailsModal } from "./JobDetailsModal";
import { FilesModal } from "./FilesModal";
import { LinksModal } from "./LinksModal";
import { CheckCircleIcon, FolderIcon, FunnelIcon, LinkIcon, PowerIcon, RefreshIcon, SortIcon, UndoCircleIcon } from "./Icons";
import { compactDateTime } from "./time";
import "./App.css";

/** How often the list auto-refreshes on its own, shown to the person as a countdown. */
const AUTO_REFRESH_SECONDS = 30;

/** "APPLIED" is its own bucket here (not a JobStatus) so it lines up with the row/modal display, where
 *  the Applied badge already supersedes the resume-generation status once a job is marked applied. */
type StatusFilter = "ALL" | "NEW" | "RESUME_READY" | "FAILED" | "APPLIED";

const FILTER_LABELS: Record<StatusFilter, string> = {
    ALL: "All",
    NEW: "Pending",
    RESUME_READY: "Ready",
    FAILED: "Failed",
    APPLIED: "Applied",
};

function matchesFilter(job: JobSummary, filter: StatusFilter): boolean {
    if (filter === "ALL") return true;
    if (filter === "APPLIED") return job.applied;
    return !job.applied && job.status === filter;
}

type SortBy = "recent" | "fit";

const SORT_LABELS: Record<SortBy, string> = {
    recent: "Most recent",
    fit: "Highest fit",
};

/** The list from the API already comes back createdAt-desc (see JobController.list), so "recent" is a
 *  no-op - only "fit" needs an actual sort here. Jobs with no fit score yet (still calculating, or failed)
 *  sort to the bottom rather than being treated as a 0% match. */
function sortJobs(jobs: JobSummary[], sortBy: SortBy): JobSummary[] {
    if (sortBy === "recent") return jobs;
    return [...jobs].sort((a, b) => {
        const af = a.fitStatus === "READY" && a.fitPercentage !== null ? a.fitPercentage : -1;
        const bf = b.fitStatus === "READY" && b.fitPercentage !== null ? b.fitPercentage : -1;
        return bf - af;
    });
}

export default function App() {
    const [jobs, setJobs] = useState<JobSummary[] | null>(null);
    const [pendingIntakes, setPendingIntakes] = useState<PendingIntakeSummary[]>([]);
    const [error, setError] = useState<string | null>(null);
    const [secondsUntilRefresh, setSecondsUntilRefresh] = useState(AUTO_REFRESH_SECONDS);
    const [pendingDelete, setPendingDelete] = useState<JobSummary | null>(null);
    const [deleteFolderToo, setDeleteFolderToo] = useState(false);
    const [deleting, setDeleting] = useState(false);
    const [deleteError, setDeleteError] = useState<string | null>(null);
    const [confirmingShutdown, setConfirmingShutdown] = useState(false);
    const [shutdownState, setShutdownState] = useState<{ scheduledInSeconds: number } | { error: string } | null>(
        null
    );
    const [shuttingDown, setShuttingDown] = useState(false);
    const [openJob, setOpenJob] = useState<JobSummary | null>(null);
    const [filesJob, setFilesJob] = useState<JobSummary | null>(null);
    const [appliedBusyId, setAppliedBusyId] = useState<string | null>(null);
    const [statusFilter, setStatusFilter] = useState<StatusFilter>("ALL");
    const [filterOpen, setFilterOpen] = useState(false);
    const [sortBy, setSortBy] = useState<SortBy>("recent");
    const [sortOpen, setSortOpen] = useState(false);
    const sortRef = useRef<HTMLDivElement>(null);
    const [linksOpen, setLinksOpen] = useState(false);
    const filterRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        if (!filterOpen) return;
        function onDocClick(e: MouseEvent) {
            if (filterRef.current && !filterRef.current.contains(e.target as Node)) {
                setFilterOpen(false);
            }
        }
        document.addEventListener("mousedown", onDocClick);
        return () => document.removeEventListener("mousedown", onDocClick);
    }, [filterOpen]);

    useEffect(() => {
        if (!sortOpen) return;
        function onDocClick(e: MouseEvent) {
            if (sortRef.current && !sortRef.current.contains(e.target as Node)) {
                setSortOpen(false);
            }
        }
        document.addEventListener("mousedown", onDocClick);
        return () => document.removeEventListener("mousedown", onDocClick);
    }, [sortOpen]);

    function load() {
        setError(null);
        Promise.all([fetchJobs(), fetchPendingIntakes()])
            .then(([jobsData, pendingData]) => {
                setJobs(jobsData);
                setPendingIntakes(pendingData);
                // Any load - manual, SSE-triggered, or the auto-refresh timer itself - restarts the
                // countdown, so it always reads "time until the NEXT refresh" rather than drifting out of
                // sync with what's actually on screen.
                setSecondsUntilRefresh(AUTO_REFRESH_SECONDS);
            })
            .catch((e: Error) => setError(e.message));
    }

    useEffect(() => {
        load();
        // Live updates: a job created, its status changing (e.g. resume finished), a posting being
        // read/becoming ambiguous/failing, or a job being deleted - in this tab or any other - refetches
        // both lists automatically, no manual Refresh needed for that.
        const unsubscribe = subscribeToJobEvents(load);

        // Auto-refresh whenever this tab becomes visible again (e.g. switching back from another tab
        // or app) - catches anything the SSE connection might have missed while the tab was backgrounded.
        function onVisible() {
            if (document.visibilityState === "visible") {
                load();
            }
        }
        document.addEventListener("visibilitychange", onVisible);

        return () => {
            unsubscribe();
            document.removeEventListener("visibilitychange", onVisible);
        };
    }, []);

    // Counts down to the next auto-refresh, once a second; hitting 0 triggers it (load() then restarts
    // the countdown itself, once the fetch actually succeeds).
    useEffect(() => {
        const id = setInterval(() => {
            setSecondsUntilRefresh((s) => {
                if (s <= 1) {
                    load();
                    return AUTO_REFRESH_SECONDS;
                }
                return s - 1;
            });
        }, 1000);
        return () => clearInterval(id);
    }, []);

    async function confirmShutdown() {
        setShuttingDown(true);
        try {
            const result = await requestShutdown();
            setShutdownState(result);
        } catch (e) {
            setShutdownState({ error: (e as Error).message });
        } finally {
            setShuttingDown(false);
            setConfirmingShutdown(false);
        }
    }

    async function confirmDelete() {
        if (!pendingDelete) return;
        setDeleting(true);
        setDeleteError(null);
        try {
            await deleteJob(pendingDelete.id, deleteFolderToo);
            setJobs((prev) => prev?.filter((j) => j.id !== pendingDelete.id) ?? prev);
            setOpenJob(null);
            setPendingDelete(null);
        } catch (e) {
            setDeleteError((e as Error).message);
        } finally {
            setDeleting(false);
        }
    }

    async function handleOpenFolder(job: JobSummary) {
        try {
            await openJobFolder(job.id);
        } catch (e) {
            setError((e as Error).message);
        }
    }

    async function toggleApplied(job: JobSummary) {
        setAppliedBusyId(job.id);
        try {
            const updated = await setJobApplied(job.id, !job.applied);
            setJobs((prev) => prev?.map((j) => (j.id === updated.id ? updated : j)) ?? prev);
            setOpenJob((prev) => (prev && prev.id === updated.id ? updated : prev));
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setAppliedBusyId(null);
        }
    }

    async function saveLocation(job: JobSummary, location: string) {
        const updated = await setJobLocation(job.id, location);
        setJobs((prev) => prev?.map((j) => (j.id === updated.id ? updated : j)) ?? prev);
        setOpenJob((prev) => (prev && prev.id === updated.id ? updated : prev));
    }

    async function handleGenerateCoverLetter(job: JobSummary) {
        const updated = await generateCoverLetter(job.id);
        setJobs((prev) => prev?.map((j) => (j.id === updated.id ? updated : j)) ?? prev);
        setOpenJob((prev) => (prev && prev.id === updated.id ? updated : prev));
        // The real result (READY/FAILED) arrives later via the SSE-triggered load() once the background
        // claude -p call + PDF render finish - this just reflects the immediate PENDING state.
    }

    /** generateReferralResume/CoverLetter return a JobDetail (extends JobSummary), so this can update the
     *  `jobs` list exactly like handleGenerateCoverLetter does above. */
    async function handleGenerateReferralResume(job: JobSummary) {
        const updated = await generateReferralResume(job.id);
        setJobs((prev) => prev?.map((j) => (j.id === updated.id ? updated : j)) ?? prev);
        setOpenJob((prev) => (prev && prev.id === updated.id ? updated : prev));
    }

    async function handleGenerateReferralCoverLetter(job: JobSummary) {
        const updated = await generateReferralCoverLetter(job.id);
        setJobs((prev) => prev?.map((j) => (j.id === updated.id ? updated : j)) ?? prev);
        setOpenJob((prev) => (prev && prev.id === updated.id ? updated : prev));
    }

    const filteredJobs = jobs
        ? sortJobs(jobs.filter((j) => matchesFilter(j, statusFilter)), sortBy)
        : null;

    return (
        <main>
            <header className="page-header">
                <div className="page-title">
                    <h1>Applications</h1>
                    <span className="refreshed-at" title="Auto-refreshes every 30s; click Refresh for now">
                        Refreshing in {secondsUntilRefresh}s
                    </span>
                </div>
                <div className="header-actions">
                    <div className="filter-wrap" ref={filterRef}>
                        <button
                            className={`icon-btn ${statusFilter !== "ALL" ? "is-active" : ""}`}
                            onClick={() => setFilterOpen((o) => !o)}
                            title={`Filter by status${statusFilter !== "ALL" ? ` (${FILTER_LABELS[statusFilter]})` : ""}`}
                            aria-label="Filter by status"
                        >
                            <FunnelIcon />
                        </button>
                        {filterOpen && (
                            <div className="filter-menu">
                                {(Object.keys(FILTER_LABELS) as StatusFilter[]).map((key) => (
                                    <button
                                        key={key}
                                        className={`filter-option ${statusFilter === key ? "is-selected" : ""}`}
                                        onClick={() => {
                                            setStatusFilter(key);
                                            setFilterOpen(false);
                                        }}
                                    >
                                        {FILTER_LABELS[key]}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                    <div className="filter-wrap" ref={sortRef}>
                        <button
                            className={`icon-btn ${sortBy !== "recent" ? "is-active" : ""}`}
                            onClick={() => setSortOpen((o) => !o)}
                            title={`Sort (${SORT_LABELS[sortBy]})`}
                            aria-label="Sort"
                        >
                            <SortIcon />
                        </button>
                        {sortOpen && (
                            <div className="filter-menu">
                                {(Object.keys(SORT_LABELS) as SortBy[]).map((key) => (
                                    <button
                                        key={key}
                                        className={`filter-option ${sortBy === key ? "is-selected" : ""}`}
                                        onClick={() => {
                                            setSortBy(key);
                                            setSortOpen(false);
                                        }}
                                    >
                                        {SORT_LABELS[key]}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>
                    <button className="icon-btn" onClick={load} title="Refresh now" aria-label="Refresh now">
                        <RefreshIcon />
                    </button>
                    <button
                        className="icon-btn"
                        onClick={() => setLinksOpen(true)}
                        title="Your links (LinkedIn, GitHub, …)"
                        aria-label="Your links"
                    >
                        <LinkIcon />
                    </button>
                    <button
                        className="icon-btn danger"
                        onClick={() => setConfirmingShutdown(true)}
                        title="Shut down server"
                        aria-label="Shut down server"
                    >
                        <PowerIcon />
                    </button>
                </div>
            </header>

            <PendingIntakes items={pendingIntakes} onChanged={load} />

            {shutdownState && "scheduledInSeconds" in shutdownState && (
                <p className="shutdown-banner">
                    Shutting down in about {Math.round(shutdownState.scheduledInSeconds / 60)} minute unless the app
                    is used again first.{" "}
                    <button
                        onClick={() => {
                            load();
                            setShutdownState(null);
                        }}
                    >
                        Cancel (use the app)
                    </button>
                </p>
            )}
            {shutdownState && "error" in shutdownState && <p className="error">{shutdownState.error}</p>}

            {error && <p className="error">{error}</p>}
            {!error && jobs === null && <p>Loading…</p>}
            {!error && jobs !== null && jobs.length === 0 && <p>No applications yet.</p>}
            {!error && jobs !== null && jobs.length > 0 && filteredJobs?.length === 0 && (
                <p>No applications match this filter.</p>
            )}

            {filteredJobs !== null && filteredJobs.length > 0 && (
                <div className="table-wrap">
                <table>
                    <thead>
                        <tr>
                            <th>Job</th>
                            <th>Status</th>
                            <th></th>
                        </tr>
                    </thead>
                    <tbody>
                        {filteredJobs.map((job) => (
                            <tr
                                key={job.id}
                                className="job-row"
                                title={job.status === "FAILED" ? job.lastError ?? "" : undefined}
                                onClick={() => setOpenJob(job)}
                            >
                                <td>
                                    <div className="job-cell">
                                        <span className="job-role">{job.role}</span>
                                        <span className="job-company">{job.company}</span>
                                    </div>
                                </td>
                                <td>
                                    {/* Only the most-advanced status shows here - Applied supersedes the
                                        resume-generation status once set, rather than stacking both badges.
                                        The date+time sits below it, in the same cell, instead of its own
                                        column - keeps the row narrow enough for the extension side panel. */}
                                    <div className="status-cell">
                                        <div className="status-badges">
                                            {job.applied ? (
                                                <AppliedBadge />
                                            ) : (
                                                <StatusBadge status={job.status} />
                                            )}
                                            {job.fitStatus === "READY" && job.fitPercentage !== null && (
                                                <span title={job.fitSummary ?? undefined}>
                                                    <FitBadge percentage={job.fitPercentage} />
                                                </span>
                                            )}
                                        </div>
                                        <span className="status-sub" title={new Date(job.createdAt).toLocaleString()}>
                                            {compactDateTime(new Date(job.createdAt))}
                                        </span>
                                    </div>
                                </td>
                                <td className="actions-cell" onClick={(e) => e.stopPropagation()}>
                                    <button
                                        className="icon-btn"
                                        onClick={() => setFilesJob(job)}
                                        title={job.folderName}
                                        aria-label="View files"
                                    >
                                        <FolderIcon width={13} height={13} />
                                    </button>
                                    <button
                                        className={`icon-btn applied-toggle ${job.applied ? "is-applied" : ""}`}
                                        disabled={appliedBusyId === job.id}
                                        onClick={() => toggleApplied(job)}
                                        title={job.applied ? "Mark as not applied" : "Mark as applied"}
                                        aria-label={job.applied ? "Mark as not applied" : "Mark as applied"}
                                    >
                                        {job.applied ? (
                                            <UndoCircleIcon width={13} height={13} />
                                        ) : (
                                            <CheckCircleIcon width={13} height={13} />
                                        )}
                                    </button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                </div>
            )}

            {openJob && (
                <JobDetailsModal
                    job={jobs?.find((j) => j.id === openJob.id) ?? openJob}
                    onClose={() => setOpenJob(null)}
                    onToggleApplied={toggleApplied}
                    appliedBusy={appliedBusyId === openJob.id}
                    onSaveLocation={saveLocation}
                    onOpenFolder={(job) => setFilesJob(job)}
                    onGenerateReferralResume={handleGenerateReferralResume}
                    onGenerateReferralCoverLetter={handleGenerateReferralCoverLetter}
                    onDelete={(job) => {
                        setDeleteError(null);
                        setDeleteFolderToo(false);
                        setPendingDelete(job);
                    }}
                />
            )}

            {filesJob && (
                <FilesModal
                    job={jobs?.find((j) => j.id === filesJob.id) ?? filesJob}
                    onClose={() => setFilesJob(null)}
                    onOpenFolder={handleOpenFolder}
                    onGenerateCoverLetter={handleGenerateCoverLetter}
                />
            )}

            {pendingDelete && (
                <ConfirmDialog
                    title="Delete this application?"
                    message={`This removes ${pendingDelete.company} — ${pendingDelete.role} from the tracker.`}
                    busy={deleting}
                    busyLabel="Deleting…"
                    onConfirm={confirmDelete}
                    onCancel={() => setPendingDelete(null)}
                >
                    <label className="dialog-checkbox">
                        <input
                            type="checkbox"
                            checked={deleteFolderToo}
                            onChange={(e) => setDeleteFolderToo(e.target.checked)}
                            disabled={deleting}
                        />
                        Also delete its generated folder (resume + job description PDF)
                    </label>
                </ConfirmDialog>
            )}
            {deleteError && <p className="error">{deleteError}</p>}

            {linksOpen && <LinksModal onClose={() => setLinksOpen(false)} />}

            {confirmingShutdown && (
                <ConfirmDialog
                    title="Shut down the server?"
                    message="It stays up for about a minute in case you didn't mean it - using the app again in that time (even just Refresh) cancels the shutdown."
                    confirmLabel="Shut down"
                    busy={shuttingDown}
                    onConfirm={confirmShutdown}
                    onCancel={() => setConfirmingShutdown(false)}
                />
            )}
        </main>
    );
}
