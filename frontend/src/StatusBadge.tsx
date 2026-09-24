import type { JobStatus } from "./api";
import { CheckCircleIcon } from "./Icons";

const LABELS: Record<JobStatus, string> = {
    NEW: "Pending",
    RESUME_READY: "Ready",
    FAILED: "Failed",
};

export function StatusBadge({ status }: { status: JobStatus }) {
    return <span className={`badge badge-${status.toLowerCase()}`}>{LABELS[status]}</span>;
}

/** Small "Applied" pill shown next to the status badge once marked - distinct from the resume-generation status. */
export function AppliedBadge() {
    return (
        <span className="badge badge-applied">
            <CheckCircleIcon width={12} height={12} /> Applied
        </span>
    );
}

/** Computed automatically for every job - green/yellow/red by how strong the match is. */
export function FitBadge({ percentage }: { percentage: number }) {
    const tier = percentage >= 75 ? "high" : percentage >= 50 ? "mid" : "low";
    return <span className={`badge badge-fit-${tier}`}>{percentage}% fit</span>;
}
