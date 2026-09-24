import { useState } from "react";
import { askJobHelp, type JobSummary } from "./api";
import { CheckCircleIcon, CloseIcon, CopyIcon, SendIcon } from "./Icons";

interface Props {
    job: JobSummary;
    onClose: () => void;
}

interface QnA {
    question: string;
    answer: string;
}

/**
 * A per-job "ask Claude" helper - for the free-form questions a company's application form asks (why this
 * role, availability, notice period, etc.) that aren't covered by the resume/cover letter/referral note.
 * Keeps a running list for this session (not persisted - closing the modal loses it) so several questions
 * about the same job can be asked one after another without losing earlier answers.
 */
export function HelpModal({ job, onClose }: Props) {
    const [question, setQuestion] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [history, setHistory] = useState<QnA[]>([]);
    const [copiedIndex, setCopiedIndex] = useState<number | null>(null);

    async function ask() {
        const q = question.trim();
        if (!q || busy) return;
        setBusy(true);
        setError(null);
        try {
            const answer = await askJobHelp(job.id, q);
            setHistory((prev) => [...prev, { question: q, answer }]);
            setQuestion("");
        } catch (e) {
            setError((e as Error).message);
        } finally {
            setBusy(false);
        }
    }

    async function copy(index: number, text: string) {
        try {
            await navigator.clipboard.writeText(text);
            setCopiedIndex(index);
            setTimeout(() => setCopiedIndex(null), 2000);
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

                <h2>Ask about this application</h2>
                <p className="job-modal-company">
                    {job.role} — {job.company}
                </p>
                <p className="help-hint">
                    Paste a question straight from the application form (e.g. "Why do you want to work here?")
                    and Claude will answer it on your behalf, using your real background. Always review before
                    pasting into the actual form.
                </p>

                {error && <p className="error">{error}</p>}

                {history.length > 0 && (
                    <div className="help-history">
                        {history.map((qa, i) => (
                            <div className="help-entry" key={i}>
                                <p className="help-question">{qa.question}</p>
                                <div className="help-answer">
                                    <p>{qa.answer}</p>
                                    <button className="icon-btn" onClick={() => copy(i, qa.answer)} title="Copy answer">
                                        {copiedIndex === i ? (
                                            <CheckCircleIcon width={13} height={13} />
                                        ) : (
                                            <CopyIcon width={13} height={13} />
                                        )}
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                <div className="help-ask-row">
                    <textarea
                        className="help-question-input"
                        rows={2}
                        placeholder="Ask a question about this application…"
                        value={question}
                        disabled={busy}
                        onChange={(e) => setQuestion(e.target.value)}
                        onKeyDown={(e) => {
                            if (e.key === "Enter" && !e.shiftKey) {
                                e.preventDefault();
                                ask();
                            }
                        }}
                    />
                    <button className="primary" disabled={busy || !question.trim()} onClick={ask}>
                        {busy ? <span className="spinner" /> : <SendIcon width={14} height={14} />}
                        {busy ? "Asking…" : "Ask"}
                    </button>
                </div>
            </div>
        </div>
    );
}
