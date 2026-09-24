import { useEffect, useState } from "react";
import { fetchJobDetail, setJobId as apiSetJobId, type JobDetail, type JobSummary } from "./api";
import { AppliedBadge, FitBadge, StatusBadge } from "./StatusBadge";
import {
    BuildingIcon,
    CheckCircleIcon,
    CloseIcon,
    CopyIcon,
    FolderIcon,
    HashIcon,
    HelpCircleIcon,
    LinkIcon,
    MailIcon,
    MapPinIcon,
    PlusIcon,
    TrashIcon,
    UndoCircleIcon,
} from "./Icons";
import { HelpModal } from "./HelpModal";
import { ReferralNoteModal } from "./ReferralNoteModal";
import { shortDateTime } from "./time";

/** "Associate Software Engineer" when THIS job's own role mentions "Associate", else plain "Software
 *  Engineer" - the candidate's own title at Compro Technologies, tailored to read naturally against the
 *  seniority of the role being applied to. Mirrors ResumeGeneratorService.designationFor on the backend
 *  (kept in sync by hand - this is display-only, never sent anywhere). */
function designationFor(role: string): string {
    return role.toLowerCase().includes("associate") ? "Associate Software Engineer" : "Software Engineer";
}

/** A short, editable blurb describing the current role at Compro Technologies - for application-form
 *  fields that ask "describe your current position" rather than just a one-line title. Built from the same
 *  real facts as the resume/cover letter (see templates/coverletter/profile.txt), condensed to a couple of
 *  sentences; freely editable before copying since forms vary in how much detail they want. */
function defaultRoleDescription(role: string): string {
    // Every line is taken from templates/coverletter/profile.txt (the same facts as the resume) - nothing
    // here may claim more than that file does.
    return [
        `${designationFor(role)} at Compro Technologies, Delhi (Jan 2025 - Present)`,
        "Project: K-12 LMS (School Framework) - a multi-tenant platform supporting 1M+ active users across schools.",
        "",
        "• Built the core Roster Service modelling 6 OneRoster entities (organizations, users, courses, classes, enrollments, academic sessions) per the 1EdTech OneRoster standard, with dedicated Lambda handlers per entity.",
        "• Integrated Rostersteam (Ednition) as the roster ingestion source, consuming OneRoster-formatted CSV data through event-driven Lambda handlers to automate onboarding across multiple schools.",
        "• Built a Calendar Service end to end (event modelling, ingestion, calendar-date processing per the EdFi schema) with a CQRS-based read model for user-course aggregations and progress tracking, improving read throughput by roughly 35% while supporting multi-tenancy.",
        "• Architected an event-driven transcript generation platform using AWS SQS, Lambda, DynamoDB, API Gateway and S3, maintaining 99.9% availability.",
        "• Shipped 15+ REST APIs and 10+ GraphQL APIs with JWT authentication, request validation, caching, rate limiting and centralized structured logging, reducing incident-investigation time by roughly 30%.",
        "• Built and documented an internal service SDK and provisioned infrastructure with AWS CDK across 5 microservices.",
        "• Cut frontend load times (FCP and LCP by ~0.5s) through image optimization, lazy loading and code splitting, and refactored 15+ legacy modules into a reusable component library.",
        "• Fixed a cross-tenant session-leakage vulnerability across the Onboarding and Teaching applications, restoring correct tenant, session and domain isolation.",
    ].join("\n");
}

interface Props {
    job: JobSummary;
    onClose: () => void;
    onToggleApplied: (job: JobSummary) => void;
    appliedBusy: boolean;
    onSaveLocation: (job: JobSummary, location: string) => Promise<void>;
    /** Opens the Files modal (FilesModal) for this job - despite the name, this no longer jumps straight
     *  to the OS file explorer; that's now one action *inside* the Files modal instead. The cover-letter
     *  "Generate" control also lives there now, next to the files it produces. */
    onOpenFolder: (job: JobSummary) => void;
    onGenerateReferralResume: (job: JobSummary) => Promise<void>;
    onGenerateReferralCoverLetter: (job: JobSummary) => Promise<void>;
    onDelete: (job: JobSummary) => void;
}

/** Row-click detail view: everything the list row doesn't show, plus the only Delete button in the UI. */
export function JobDetailsModal({
    job,
    onClose,
    onToggleApplied,
    appliedBusy,
    onSaveLocation,
    onOpenFolder,
    onGenerateReferralResume,
    onGenerateReferralCoverLetter,
    onDelete,
}: Props) {
    const [detail, setDetail] = useState<JobDetail | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [editingLocation, setEditingLocation] = useState(false);
    const [locationDraft, setLocationDraft] = useState("");
    const [savingLocation, setSavingLocation] = useState(false);
    const [editingJobId, setEditingJobId] = useState(false);
    const [jobIdDraft, setJobIdDraft] = useState("");
    const [savingJobId, setSavingJobId] = useState(false);
    const [referralOpen, setReferralOpen] = useState(false);
    const [fitExpanded, setFitExpanded] = useState(false);
    const [helpOpen, setHelpOpen] = useState(false);
    const [roleDescriptionDraft, setRoleDescriptionDraft] = useState(() => defaultRoleDescription(job.role));
    const [roleDescriptionCopied, setRoleDescriptionCopied] = useState(false);

    useEffect(() => {
        let cancelled = false;
        fetchJobDetail(job.id)
            .then((d) => !cancelled && setDetail(d))
            .catch((e: Error) => !cancelled && setError(e.message));
        return () => {
            cancelled = true;
        };
    }, [job.id]);

    async function saveLocation() {
        setSavingLocation(true);
        try {
            await onSaveLocation(job, locationDraft.trim());
            setEditingLocation(false);
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setSavingLocation(false);
        }
    }

    /**
     * Corrects (or clears) the extracted job id by hand - handled entirely within this modal (unlike
     * location, it isn't shown anywhere else in the UI - the row list and cover letter/referral note read
     * it straight from the job, not from any App-level state), so this just patches local `detail` state.
     */
    async function saveJobId() {
        const trimmed = jobIdDraft.trim();
        setSavingJobId(true);
        try {
            await apiSetJobId(job.id, trimmed);
            setDetail((prev) => (prev ? { ...prev, jobId: trimmed || null } : prev));
            setEditingJobId(false);
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setSavingJobId(false);
        }
    }

    async function copyRoleDescription() {
        try {
            await navigator.clipboard.writeText(roleDescriptionDraft);
            setRoleDescriptionCopied(true);
            setTimeout(() => setRoleDescriptionCopied(false), 2000);
        } catch {
            // Clipboard permission denied or unavailable - the text is still selectable by hand.
        }
    }

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog job-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
                <button className="dialog-close" onClick={onClose} aria-label="Close">
                    <CloseIcon />
                </button>

                <h2>{job.role}</h2>
                <p className="job-modal-company">
                    <BuildingIcon width={14} height={14} /> {job.company}
                </p>

                <div className="job-modal-badges">
                    <StatusBadge status={job.status} />
                    {job.applied && <AppliedBadge />}
                    {job.fitStatus === "READY" && job.fitPercentage !== null && (
                        <button
                            className="fit-badge-btn"
                            onClick={() => setFitExpanded((e) => !e)}
                            title="Click to see why"
                        >
                            <FitBadge percentage={job.fitPercentage} />
                        </button>
                    )}
                    {job.fitStatus === "PENDING" && (
                        <span className="fit-calculating">
                            <span className="spinner" /> Calculating fit…
                        </span>
                    )}
                </div>

                {/* Hidden by default - the reason can run long, so it only shows once asked for by
                    clicking the fit badge above, rather than always taking up space in the facts list. */}
                {fitExpanded && job.fitStatus === "READY" && job.fitSummary && (
                    <p className="job-modal-fit-summary">{job.fitSummary}</p>
                )}

                {error && <p className="error">{error}</p>}

                <dl className="job-modal-facts">
                    <dt>Created</dt>
                    <dd>{shortDateTime(new Date(job.createdAt))}</dd>

                    {job.applied && job.appliedAt && (
                        <>
                            <dt>Applied</dt>
                            <dd>{shortDateTime(new Date(job.appliedAt))}</dd>
                        </>
                    )}

                    <dt>Location</dt>
                    <dd>
                        {editingLocation ? (
                            <span className="fact-edit">
                                <input
                                    autoFocus
                                    className="fact-input"
                                    value={locationDraft}
                                    placeholder="e.g. Bengaluru, India"
                                    disabled={savingLocation}
                                    onChange={(e) => setLocationDraft(e.target.value)}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter") saveLocation();
                                        if (e.key === "Escape") setEditingLocation(false);
                                    }}
                                />
                                <button disabled={savingLocation} onClick={saveLocation}>
                                    {savingLocation ? "Saving…" : "Save"}
                                </button>
                                <button disabled={savingLocation} onClick={() => setEditingLocation(false)}>
                                    Cancel
                                </button>
                            </span>
                        ) : job.location ? (
                            <span
                                className="fact-value-editable"
                                onClick={() => {
                                    setLocationDraft(job.location ?? "");
                                    setEditingLocation(true);
                                }}
                                title="Click to edit"
                            >
                                <MapPinIcon width={13} height={13} /> {job.location}
                            </span>
                        ) : (
                            <button
                                className="add-fact-btn"
                                onClick={() => {
                                    setLocationDraft("");
                                    setEditingLocation(true);
                                }}
                            >
                                <PlusIcon width={13} height={13} /> Add location
                            </button>
                        )}
                    </dd>

                    {/* Waits for `detail` (unlike Location, which is on the always-available `job` prop) so
                        this doesn't flash "Add job ID" for a moment before the real value loads in. */}
                    {detail && (
                    <>
                    <dt>Job ID</dt>
                    <dd>
                        {editingJobId ? (
                            <span className="fact-edit">
                                <input
                                    autoFocus
                                    className="fact-input"
                                    value={jobIdDraft}
                                    placeholder="e.g. R-246001"
                                    disabled={savingJobId}
                                    onChange={(e) => setJobIdDraft(e.target.value)}
                                    onKeyDown={(e) => {
                                        if (e.key === "Enter") saveJobId();
                                        if (e.key === "Escape") setEditingJobId(false);
                                    }}
                                />
                                <button disabled={savingJobId} onClick={saveJobId}>
                                    {savingJobId ? "Saving…" : "Save"}
                                </button>
                                <button disabled={savingJobId} onClick={() => setEditingJobId(false)}>
                                    Cancel
                                </button>
                            </span>
                        ) : detail?.jobId ? (
                            <span
                                className="fact-value-editable"
                                onClick={() => {
                                    setJobIdDraft(detail.jobId ?? "");
                                    setEditingJobId(true);
                                }}
                                title="Click to edit or remove"
                            >
                                <HashIcon width={13} height={13} /> {detail.jobId}
                            </span>
                        ) : (
                            <button
                                className="add-fact-btn"
                                disabled={!detail}
                                onClick={() => {
                                    setJobIdDraft("");
                                    setEditingJobId(true);
                                }}
                            >
                                <PlusIcon width={13} height={13} /> Add job ID
                            </button>
                        )}
                    </dd>
                    </>
                    )}

                    {(detail?.type || detail?.mode) && (
                        <>
                            <dt>Type</dt>
                            {/* One line, not two - Type and Mode are both single short words, splitting
                                them across rows just made the facts list taller for no reason. */}
                            <dd>{[detail?.type, detail?.mode].filter(Boolean).join(" · ")}</dd>
                        </>
                    )}
                    {detail?.stacks && detail.stacks.length > 0 && (
                        <>
                            <dt>Stacks</dt>
                            <dd>{detail.stacks.join(", ")}</dd>
                        </>
                    )}
                    {(detail?.resumeSkills?.length ? detail.resumeSkills : detail?.skills)?.length ? (
                        <>
                            <dt>Skills</dt>
                            {/* Capped to ~4 lines and scrollable rather than pushing the whole modal (and
                                its action buttons) far down the page for a long skills list. A visibly
                                styled scrollbar (not just overflow:auto's OS-default, which can render as
                                nothing until hovered) so the "there's more below" affordance is obvious. */}
                            <dd className="job-modal-skills">
                                {(detail?.resumeSkills?.length ? detail.resumeSkills : detail?.skills ?? []).join(", ")}
                            </dd>
                        </>
                    ) : null}
                    {detail?.url && (
                        <>
                            <dt>Posting</dt>
                            <dd>
                                <a href={detail.url} target="_blank" rel="noreferrer" className="job-modal-link">
                                    <LinkIcon width={13} height={13} /> Open original posting
                                </a>
                            </dd>
                        </>
                    )}
                    <dt>Folder</dt>
                    <dd>
                        <button
                            className="job-modal-link job-modal-folder-btn"
                            onClick={() => onOpenFolder(job)}
                            title="View files"
                        >
                            <FolderIcon width={13} height={13} /> {job.folderName}
                        </button>
                    </dd>
                </dl>

                <div className="role-description">
                    <p className="role-description-label">
                        Role description (Compro Technologies) - for forms asking about your current role
                    </p>
                    <div className="role-description-row">
                        <textarea
                            className="role-description-input"
                            rows={14}
                            value={roleDescriptionDraft}
                            onChange={(e) => setRoleDescriptionDraft(e.target.value)}
                        />
                        <button className="icon-btn" onClick={copyRoleDescription} title="Copy">
                            {roleDescriptionCopied ? (
                                <CheckCircleIcon width={14} height={14} />
                            ) : (
                                <CopyIcon width={14} height={14} />
                            )}
                        </button>
                    </div>
                </div>

                {job.status === "FAILED" && job.lastError && (
                    <p className="job-modal-error">{job.lastError}</p>
                )}

                <div className="job-modal-actions">
                    <div className="job-modal-actions-left">
                        <button
                            className={job.applied ? "" : "primary"}
                            disabled={appliedBusy}
                            onClick={() => onToggleApplied(job)}
                        >
                            {job.applied ? (
                                <>
                                    <UndoCircleIcon /> Mark as not applied
                                </>
                            ) : (
                                <>
                                    <CheckCircleIcon /> Mark as applied
                                </>
                            )}
                        </button>
                        <button disabled={!detail} onClick={() => setReferralOpen(true)} title="Referral note">
                            <MailIcon width={14} height={14} /> Referral note
                        </button>
                        <button onClick={() => setHelpOpen(true)} title="Ask Claude a question about this application">
                            <HelpCircleIcon width={14} height={14} /> Help
                        </button>
                    </div>
                    <button className="danger-link" onClick={() => onDelete(job)}>
                        <TrashIcon /> Delete
                    </button>
                </div>
            </div>

            {referralOpen && detail && (
                <ReferralNoteModal
                    detail={detail}
                    job={job}
                    onClose={() => setReferralOpen(false)}
                    onGenerateResume={onGenerateReferralResume}
                    onGenerateCoverLetter={onGenerateReferralCoverLetter}
                />
            )}

            {helpOpen && <HelpModal job={job} onClose={() => setHelpOpen(false)} />}
        </div>
    );
}
