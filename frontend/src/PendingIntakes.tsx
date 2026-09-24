import { useState } from "react";
import { confirmPendingIntake, rejectPendingIntake, type PendingIntakeSummary } from "./api";

interface Props {
    items: PendingIntakeSummary[];
    onChanged: () => void;
}

/**
 * Postings the extension has sent but the app hasn't finished with yet: still being read, ambiguous
 * (needs a Yes/No), or failed. This is the ONLY place an ambiguous duplicate is resolved - the extension
 * itself never asks.
 */
export function PendingIntakes({ items, onChanged }: Props) {
    const [busyId, setBusyId] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    if (items.length === 0) {
        return null;
    }

    async function run(id: string, action: () => Promise<void>) {
        setBusyId(id);
        setError(null);
        try {
            await action();
            onChanged();
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setBusyId(null);
        }
    }

    const pending = items.filter((i) => i.status === "PENDING");
    const activeCount = pending.filter((i) => i.active).length;
    const queuedCount = pending.length - activeCount;
    // PENDING rows are represented ONLY by the summary bar below now - a per-row "Reading this posting…"/
    // "Waiting in queue…" line for each one was redundant with it and just repeated the same information.
    const notable = items.filter((i) => i.status !== "PENDING");

    return (
        <section className="pending-section">
            <h2>Processing</h2>
            {error && <p className="error">{error}</p>}

            {pending.length > 0 && (
                <div className="pending-progress">
                    <div className="pending-progress-bar">
                        <div className="pending-progress-fill" />
                    </div>
                    <span className="pending-progress-label">
                        Reading {activeCount} posting{activeCount === 1 ? "" : "s"}
                        {queuedCount > 0 ? ` — ${queuedCount} in queue` : ""}
                    </span>
                </div>
            )}

            {notable.length > 0 && (
                <ul className="pending-list">
                    {notable.map((item) => (
                        <li key={item.id} className={`pending-item pending-${item.status.toLowerCase()}`}>
                        {item.status === "NEEDS_CONFIRMATION" && item.similarJob && (
                            <span className="pending-row pending-row-wrap">
                                <span>
                                    <strong>{item.company}</strong> — {item.role}: you already applied to{" "}
                                    <strong>{item.similarJob.company}</strong> — {item.similarJob.role}. Is this a
                                    different job?
                                </span>
                                <span className="pending-actions">
                                    <button
                                        disabled={busyId === item.id}
                                        onClick={() => run(item.id, () => confirmPendingIntake(item.id))}
                                    >
                                        {busyId === item.id ? "Saving…" : "Save as new"}
                                    </button>
                                    <button
                                        disabled={busyId === item.id}
                                        onClick={() => run(item.id, () => rejectPendingIntake(item.id))}
                                    >
                                        Skip
                                    </button>
                                </span>
                            </span>
                        )}

                        {item.status === "FAILED" && (
                            <span className="pending-row pending-row-wrap">
                                <span>Could not read a posting: {item.errorMessage}</span>
                                <span className="pending-actions">
                                    <button
                                        disabled={busyId === item.id}
                                        onClick={() => run(item.id, () => rejectPendingIntake(item.id))}
                                    >
                                        Dismiss
                                    </button>
                                </span>
                            </span>
                        )}
                        </li>
                    ))}
                </ul>
            )}
        </section>
    );
}
