import { useEffect, useRef, useState } from "react";
import { fetchFolderPath, fetchJobFiles, jobFileUrl, jobFileViewUrl, type JobFile, type JobSummary } from "./api";
import { isEmbedded } from "./browserContext";
import {
    CheckCircleIcon,
    CloseIcon,
    CopyIcon,
    DownloadIcon,
    ExternalLinkIcon,
    FileIcon,
    FolderIcon,
    GripIcon,
    SparklesIcon,
} from "./Icons";

interface Props {
    job: JobSummary;
    onClose: () => void;
    onOpenFolder: (job: JobSummary) => void;
    onGenerateCoverLetter: (job: JobSummary) => Promise<void>;
}

function formatSize(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    const kb = bytes / 1024;
    if (kb < 1024) return `${kb.toFixed(kb < 10 ? 1 : 0)} KB`;
    return `${(kb / 1024).toFixed(1)} MB`;
}

/** Guesses a MIME type from the extension - good enough for the handful of file types this app ever
 *  generates (PDFs), and DownloadURL just needs something plausible. */
function guessMimeType(filename: string): string {
    if (filename.toLowerCase().endsWith(".pdf")) return "application/pdf";
    return "application/octet-stream";
}

/**
 * Files actually on disk for this job (resume + job description PDF, plus a cover letter once one's been
 * generated) - lets the person drag one straight out of the browser onto their desktop, an email draft,
 * Slack, etc., instead of only being able to open the whole folder in a separate window. Chrome/Edge
 * support dragging a real file out via the "DownloadURL" drag data trick (the same mechanism Gmail uses
 * for its attachments); a plain Download button covers browsers that don't (Firefox/Safari).
 *
 * The "Generate" button is a menu rather than a single action because a cover letter is only the first
 * thing this can produce - adding another generatable file later is just another menu entry here.
 */
export function FilesModal({ job, onClose, onOpenFolder, onGenerateCoverLetter }: Props) {
    const [files, setFiles] = useState<JobFile[] | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [generateOpen, setGenerateOpen] = useState(false);
    const [coverLetterBusy, setCoverLetterBusy] = useState(false);
    const [pathCopied, setPathCopied] = useState(false);
    const generateRef = useRef<HTMLDivElement>(null);

    function loadFiles() {
        fetchJobFiles(job.id)
            .then(setFiles)
            .catch((e: Error) => setError(e.message));
    }

    useEffect(() => {
        loadFiles();
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [job.id]);

    // The cover letter PDF appears on disk once generation finishes - refetch the list right when that
    // happens, so it shows up without the person having to close and reopen this modal.
    useEffect(() => {
        if (job.coverLetterStatus === "READY") {
            loadFiles();
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [job.coverLetterStatus]);

    useEffect(() => {
        if (!generateOpen) return;
        function onDocClick(e: MouseEvent) {
            if (generateRef.current && !generateRef.current.contains(e.target as Node)) {
                setGenerateOpen(false);
            }
        }
        document.addEventListener("mousedown", onDocClick);
        return () => document.removeEventListener("mousedown", onDocClick);
    }, [generateOpen]);

    async function handleGenerateCoverLetter() {
        setGenerateOpen(false);
        setCoverLetterBusy(true);
        try {
            await onGenerateCoverLetter(job);
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setCoverLetterBusy(false);
        }
    }

    async function copyFolderPath() {
        try {
            const path = await fetchFolderPath(job.id);
            await navigator.clipboard.writeText(path);
            setPathCopied(true);
            setTimeout(() => setPathCopied(false), 2000);
        } catch (e) {
            setError((e as Error).message);
        }
    }

    function handleDragStart(e: React.DragEvent, file: JobFile) {
        const url = jobFileUrl(job.id, file.name);
        e.dataTransfer.setData("DownloadURL", `${guessMimeType(file.name)}:${file.name}:${url}`);
        e.dataTransfer.effectAllowed = "copy";
    }

    const coverLetterPending = coverLetterBusy || job.coverLetterStatus === "PENDING";

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog job-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
                <button className="dialog-close" onClick={onClose} aria-label="Close">
                    <CloseIcon />
                </button>

                <div className="files-header">
                    <div>
                        <h2>Files</h2>
                        <p className="job-modal-company">
                            {job.role} — {job.company}
                        </p>
                    </div>
                    <div className="filter-wrap" ref={generateRef}>
                        <button
                            className="generate-btn"
                            disabled={coverLetterPending}
                            onClick={() => setGenerateOpen((o) => !o)}
                        >
                            {coverLetterPending ? <span className="spinner" /> : <SparklesIcon width={13} height={13} />}
                            {coverLetterPending ? "Writing…" : "Generate"}
                        </button>
                        {generateOpen && (
                            <div className="filter-menu">
                                <button className="filter-option" onClick={handleGenerateCoverLetter}>
                                    Cover letter
                                    {job.coverLetterStatus === "READY" && " (redo)"}
                                    {job.coverLetterStatus === "FAILED" && " (retry)"}
                                </button>
                            </div>
                        )}
                    </div>
                </div>

                {job.coverLetterStatus === "FAILED" && job.coverLetterError && (
                    <p className="job-modal-error">{job.coverLetterError}</p>
                )}

                {error && <p className="error">{error}</p>}
                {!error && files === null && <p>Loading…</p>}
                {!error && files !== null && files.length === 0 && (
                    <p className="files-empty">Nothing generated for this job yet.</p>
                )}

                {files !== null && files.length > 0 && (
                    <>
                        <p className="files-hint">
                            {isEmbedded ? (
                                <>
                                    Download works here. Drag-and-drop onto your desktop needs a real browser tab
                                    (Chrome blocks it from inside the extension's side panel) -{" "}
                                    <a href={window.location.href} target="_blank" rel="noreferrer">
                                        open JTracker in a new tab
                                    </a>{" "}
                                    to drag files out.
                                </>
                            ) : (
                                "Drag a file out of this window to save it (Chrome/Edge), or use Download."
                            )}
                        </p>
                        <ul className="files-list">
                            {files.map((file) => (
                                <li
                                    key={file.name}
                                    className="files-item"
                                    draggable={!isEmbedded}
                                    onDragStart={isEmbedded ? undefined : (e) => handleDragStart(e, file)}
                                >
                                    <GripIcon width={13} height={13} className="files-grip" />
                                    <FileIcon width={16} height={16} />
                                    <span className="files-name" title={file.name}>
                                        {file.name}
                                    </span>
                                    <span className="files-size">{formatSize(file.sizeBytes)}</span>
                                    <a
                                        className="icon-btn"
                                        href={jobFileViewUrl(job.id, file.name)}
                                        target="_blank"
                                        rel="noreferrer"
                                        title="Open in a new tab"
                                        aria-label={`Open ${file.name} in a new tab`}
                                        draggable={false}
                                        onClick={(e) => e.stopPropagation()}
                                    >
                                        <ExternalLinkIcon width={13} height={13} />
                                    </a>
                                    <a
                                        className="icon-btn"
                                        href={jobFileUrl(job.id, file.name)}
                                        download={file.name}
                                        title="Download"
                                        aria-label={`Download ${file.name}`}
                                        onClick={(e) => e.stopPropagation()}
                                    >
                                        <DownloadIcon width={13} height={13} />
                                    </a>
                                </li>
                            ))}
                        </ul>
                    </>
                )}

                <div className="job-modal-actions">
                    <button onClick={() => onOpenFolder(job)}>
                        <FolderIcon /> Open in file explorer
                    </button>
                    <button onClick={copyFolderPath} title="Copy the folder's full path">
                        {pathCopied ? (
                            <>
                                <CheckCircleIcon width={14} height={14} /> Copied
                            </>
                        ) : (
                            <>
                                <CopyIcon width={14} height={14} /> Copy folder path
                            </>
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}
