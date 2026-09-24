/** "just now" / "5s ago" / "3m ago" / "2h ago" / "4d ago" - falls back to a short date past a week. */
export function relativeTime(date: Date, now: Date): string {
    const seconds = Math.max(0, Math.floor((now.getTime() - date.getTime()) / 1000));
    if (seconds < 5) return "just now";
    if (seconds < 60) return `${seconds}s ago`;
    const minutes = Math.floor(seconds / 60);
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 7) return `${days}d ago`;
    return shortDate(date);
}

/** "24 Sep" or "24 Sep '25" when not this year. */
export function shortDate(date: Date): string {
    const now = new Date();
    const opts: Intl.DateTimeFormatOptions =
        date.getFullYear() === now.getFullYear()
            ? { day: "numeric", month: "short" }
            : { day: "numeric", month: "short", year: "2-digit" };
    return date.toLocaleDateString(undefined, opts);
}

/** 24-hour time, e.g. "13:20" - no AM/PM. */
function time24(date: Date): string {
    return date.toLocaleTimeString(undefined, { hour: "2-digit", minute: "2-digit", hour12: false });
}

export function shortDateTime(date: Date): string {
    return `${shortDate(date)}, ${time24(date)}`;
}

/** "24 Sep, 13:20" - tighter than shortDateTime, for narrow layouts (e.g. the extension side panel). */
export function compactDateTime(date: Date): string {
    return `${shortDate(date)}, ${time24(date)}`;
}
