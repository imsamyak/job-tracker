import type { ReactNode } from "react";

interface Props {
    title: string;
    message: string;
    confirmLabel?: string;
    busyLabel?: string;
    busy?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
    /** Extra content shown between the message and the buttons, e.g. an opt-in checkbox. */
    children?: ReactNode;
}

export function ConfirmDialog({
    title,
    message,
    confirmLabel = "Delete",
    busyLabel,
    busy = false,
    onConfirm,
    onCancel,
    children,
}: Props) {
    return (
        <div className="dialog-overlay" onClick={onCancel}>
            <div className="dialog" onClick={(e) => e.stopPropagation()} role="alertdialog" aria-modal="true">
                <h2>{title}</h2>
                <p>{message}</p>
                {children}
                <div className="dialog-actions">
                    <button onClick={onCancel} disabled={busy}>
                        Cancel
                    </button>
                    <button className="danger" onClick={onConfirm} disabled={busy}>
                        {busy ? (busyLabel ?? `${confirmLabel}…`) : confirmLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}
