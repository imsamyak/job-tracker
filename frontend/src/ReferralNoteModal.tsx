import { useState } from "react";
import {
    referralCoverLetterUrl,
    referralCoverLetterViewUrl,
    referralResumeUrl,
    referralResumeViewUrl,
    type CoverLetterStatus,
    type JobDetail,
    type JobSummary,
} from "./api";
import { isEmbedded } from "./browserContext";
import { CheckCircleIcon, CloseIcon, CopyIcon, DownloadIcon, ExternalLinkIcon, FileIcon, SparklesIcon } from "./Icons";
import { fullReferralNote, shortReferralNote } from "./referral";

interface Props {
    /** For the note text (needs jobId, which only JobDetail carries) - fetched once, not live. */
    detail: JobDetail;
    /** For the doc rows' status - the same live `job` App.tsx already refreshes via SSE, so a generation
     *  in progress is reflected here without this modal needing any polling of its own. */
    job: JobSummary;
    onClose: () => void;
    onGenerateResume: (job: JobSummary) => Promise<void>;
    onGenerateCoverLetter: (job: JobSummary) => Promise<void>;
}

type Variant = "short" | "full";

/** Guesses a MIME type from the extension - same helper as FilesModal, duplicated rather than shared since
 *  both files only ever deal with the PDFs this app generates. */
function guessMimeType(filename: string): string {
    if (filename.toLowerCase().endsWith(".pdf")) return "application/pdf";
    return "application/octet-stream";
}

/** Same "drag a real file out of the browser" trick as FilesModal.handleDragStart - the referral resume/
 *  cover letter links didn't have this before, so dragging them onto the desktop/an email/Slack silently
 *  did nothing (a plain click-to-download still worked). */
function handleDocDragStart(e: React.DragEvent, filename: string, downloadUrl: string) {
    e.dataTransfer.setData("DownloadURL", `${guessMimeType(filename)}:${filename}:${downloadUrl}`);
    e.dataTransfer.effectAllowed = "copy";
}

/**
 * Two fixed referral-note templates with this job's company/role/job-id filled in - built entirely
 * client-side (no Claude, no file - just text ready to copy) per explicit instruction. Alongside them, the
 * "referral" resume/cover letter - same content as the normal ones but with a fixed, different email (see
 * `Referral.EMAIL` on the backend) for when a referrer is passing the application along. Both are lazy,
 * generated only when asked for here.
 */
export function ReferralNoteModal({ detail, job, onClose, onGenerateResume, onGenerateCoverLetter }: Props) {
    const [variant, setVariant] = useState<Variant>("short");
    const [copied, setCopied] = useState(false);
    const [resumeBusy, setResumeBusy] = useState(false);
    const [coverLetterBusy, setCoverLetterBusy] = useState(false);
    const [docError, setDocError] = useState<string | null>(null);

    const text = variant === "short" ? shortReferralNote(detail) : fullReferralNote(detail);

    async function copy() {
        try {
            await navigator.clipboard.writeText(text);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch {
            // Clipboard permission denied or unavailable - the textarea below still lets them select-all/copy by hand.
        }
    }

    function selectVariant(next: Variant) {
        setVariant(next);
        setCopied(false);
    }

    async function handleGenerateResume() {
        setResumeBusy(true);
        setDocError(null);
        try {
            await onGenerateResume(job);
        } catch (e) {
            setDocError((e as Error).message);
        } finally {
            setResumeBusy(false);
        }
    }

    async function handleGenerateCoverLetter() {
        setCoverLetterBusy(true);
        setDocError(null);
        try {
            await onGenerateCoverLetter(job);
        } catch (e) {
            setDocError((e as Error).message);
        } finally {
            setCoverLetterBusy(false);
        }
    }

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog job-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
                <button className="dialog-close" onClick={onClose} aria-label="Close">
                    <CloseIcon />
                </button>

                <h2>Referral note</h2>
                <p className="job-modal-company">
                    {detail.role} — {detail.company}
                </p>

                <div className="referral-tabs">
                    <button
                        className={`referral-tab ${variant === "short" ? "is-active" : ""}`}
                        onClick={() => selectVariant("short")}
                    >
                        Short
                    </button>
                    <button
                        className={`referral-tab ${variant === "full" ? "is-active" : ""}`}
                        onClick={() => selectVariant("full")}
                    >
                        Full
                    </button>
                </div>

                <textarea className="referral-text" readOnly value={text} rows={variant === "short" ? 5 : 9} />

                <div className="job-modal-actions">
                    <button className="primary" onClick={copy}>
                        {copied ? (
                            <>
                                <CheckCircleIcon /> Copied
                            </>
                        ) : (
                            <>
                                <CopyIcon /> Copy to clipboard
                            </>
                        )}
                    </button>
                </div>

                <div className="referral-docs">
                    <p className="referral-docs-title">Documents (referral email)</p>
                    {docError && <p className="error">{docError}</p>}
                    <ReferralDocRow
                        label="Referral Resume"
                        status={job.referralResumeStatus}
                        busy={resumeBusy}
                        downloadUrl={referralResumeUrl(job.id)}
                        viewUrl={referralResumeViewUrl(job.id)}
                        filename="Resume_Samyak_Jain.pdf"
                        onGenerate={handleGenerateResume}
                    />
                    <ReferralDocRow
                        label="Referral Cover Letter"
                        status={job.referralCoverLetterStatus}
                        busy={coverLetterBusy}
                        downloadUrl={referralCoverLetterUrl(job.id)}
                        viewUrl={referralCoverLetterViewUrl(job.id)}
                        filename="Cover_Letter_Samyak_Jain.pdf"
                        onGenerate={handleGenerateCoverLetter}
                    />
                </div>
            </div>
        </div>
    );
}

function ReferralDocRow({
    label,
    status,
    busy,
    downloadUrl,
    viewUrl,
    filename,
    onGenerate,
}: {
    label: string;
    status: CoverLetterStatus;
    busy: boolean;
    downloadUrl: string;
    viewUrl: string;
    filename: string;
    onGenerate: () => void;
}) {
    const pending = busy || status === "PENDING";
    return (
        <div className="referral-doc-row">
            <span className="referral-doc-label">
                <FileIcon width={14} height={14} /> {label}
            </span>
            {status === "READY" && !pending ? (
                <span className="referral-doc-actions">
                    <a
                        className="icon-btn"
                        href={viewUrl}
                        target="_blank"
                        rel="noreferrer"
                        title="Open in a new tab"
                        aria-label={`Open ${label} in a new tab`}
                    >
                        <ExternalLinkIcon width={13} height={13} />
                    </a>
                    <a
                        className="icon-btn"
                        href={downloadUrl}
                        download={filename}
                        title={
                            isEmbedded
                                ? "Download (drag-and-drop needs a full browser tab, not the extension panel)"
                                : "Download (or drag out of this window to save it - Chrome/Edge)"
                        }
                        draggable={!isEmbedded}
                        onDragStart={isEmbedded ? undefined : (e) => handleDocDragStart(e, filename, downloadUrl)}
                    >
                        <DownloadIcon width={13} height={13} />
                    </a>
                    <button className="add-fact-btn" onClick={onGenerate} title="Write a new one, replacing the current file">
                        <SparklesIcon width={12} height={12} /> Regenerate
                    </button>
                </span>
            ) : (
                <button className="add-fact-btn" disabled={pending} onClick={onGenerate}>
                    {pending ? (
                        <>
                            <span className="spinner" /> Writing…
                        </>
                    ) : status === "FAILED" ? (
                        <>
                            <SparklesIcon width={12} height={12} /> Retry
                        </>
                    ) : (
                        <>
                            <SparklesIcon width={12} height={12} /> Generate
                        </>
                    )}
                </button>
            )}
        </div>
    );
}
