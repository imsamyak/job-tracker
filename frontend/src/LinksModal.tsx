import { useState } from "react";
import { CheckCircleIcon, CloseIcon, CopyIcon, PlusIcon, TrashIcon } from "./Icons";

interface ProfileLink {
    id: string;
    label: string;
    url: string;
}

const STORAGE_KEY = "jtracker.profileLinks";

/** Seeded from the same contact details already baked into the resume template (see
 *  templates/resume/template.html) - a sensible starting point, freely editable/removable from here. */
const DEFAULT_LINKS: ProfileLink[] = [
    { id: "linkedin", label: "LinkedIn", url: "https://linkedin.com/in/imsamyakjain" },
    { id: "github", label: "GitHub", url: "https://github.com/imsamyak" },
    { id: "leetcode", label: "LeetCode", url: "https://leetcode.com/u/imsamyak18" },
    { id: "email", label: "Email", url: "imsamyak18@gmail.com" },
    { id: "phone", label: "Phone", url: "8766342062" },
];

function loadLinks(): ProfileLink[] {
    try {
        const raw = localStorage.getItem(STORAGE_KEY);
        if (!raw) return DEFAULT_LINKS;
        const parsed = JSON.parse(raw);
        return Array.isArray(parsed) && parsed.length > 0 ? parsed : DEFAULT_LINKS;
    } catch {
        return DEFAULT_LINKS;
    }
}

function saveLinks(links: ProfileLink[]) {
    try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(links));
    } catch {
        // Private window / storage disabled - the list just won't survive a reload, not worth surfacing.
    }
}

interface Props {
    onClose: () => void;
}

/**
 * A quick-copy list of personal links (LinkedIn, GitHub, etc.) for pasting into application forms - freely
 * editable, stored in this browser's localStorage only (this is a personal single-user local tool, so
 * there's no need for a backend record for something this small).
 */
export function LinksModal({ onClose }: Props) {
    const [links, setLinks] = useState<ProfileLink[]>(loadLinks);
    const [copiedId, setCopiedId] = useState<string | null>(null);
    const [newLabel, setNewLabel] = useState("");
    const [newUrl, setNewUrl] = useState("");

    function persist(next: ProfileLink[]) {
        setLinks(next);
        saveLinks(next);
    }

    async function copy(link: ProfileLink) {
        try {
            await navigator.clipboard.writeText(link.url);
            setCopiedId(link.id);
            setTimeout(() => setCopiedId((id) => (id === link.id ? null : id)), 2000);
        } catch {
            // Clipboard permission denied or unavailable - the value is still visible to select by hand.
        }
    }

    function removeLink(id: string) {
        persist(links.filter((l) => l.id !== id));
    }

    function addLink() {
        const label = newLabel.trim();
        const url = newUrl.trim();
        if (!label || !url) return;
        persist([...links, { id: crypto.randomUUID(), label, url }]);
        setNewLabel("");
        setNewUrl("");
    }

    return (
        <div className="dialog-overlay" onClick={onClose}>
            <div className="dialog job-modal" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
                <button className="dialog-close" onClick={onClose} aria-label="Close">
                    <CloseIcon />
                </button>

                <h2>Your links</h2>
                <p>Quick-copy for application forms - LinkedIn, GitHub, and anything else you add.</p>

                <ul className="links-list">
                    {links.map((link) => (
                        <li key={link.id} className="links-item">
                            <span className="links-label">{link.label}</span>
                            <span className="links-url" title={link.url}>
                                {link.url}
                            </span>
                            <button className="icon-btn" onClick={() => copy(link)} title="Copy">
                                {copiedId === link.id ? (
                                    <CheckCircleIcon width={13} height={13} />
                                ) : (
                                    <CopyIcon width={13} height={13} />
                                )}
                            </button>
                            <button className="icon-btn danger" onClick={() => removeLink(link.id)} title="Remove">
                                <TrashIcon width={13} height={13} />
                            </button>
                        </li>
                    ))}
                    {links.length === 0 && <p className="files-empty">No links yet - add one below.</p>}
                </ul>

                <div className="links-add-row">
                    <input
                        className="fact-input"
                        placeholder="Label (e.g. Portfolio)"
                        value={newLabel}
                        onChange={(e) => setNewLabel(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && addLink()}
                    />
                    <input
                        className="fact-input"
                        placeholder="URL or value"
                        value={newUrl}
                        onChange={(e) => setNewUrl(e.target.value)}
                        onKeyDown={(e) => e.key === "Enter" && addLink()}
                    />
                    <button disabled={!newLabel.trim() || !newUrl.trim()} onClick={addLink}>
                        <PlusIcon width={13} height={13} /> Add
                    </button>
                </div>
            </div>
        </div>
    );
}
