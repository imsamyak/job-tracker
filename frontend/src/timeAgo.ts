/** "just now", "5 min ago", "3 h ago", "2 d ago", or "not yet" when there is no time. */
export function ago(iso: string | null): string {
  if (!iso) return 'not yet'
  const min = Math.max(0, Math.round((Date.now() - Date.parse(iso)) / 60000))
  if (min < 1) return 'just now'
  if (min < 60) return `${min} min ago`
  if (min < 1440) return `${Math.round(min / 60)} h ago`
  return `${Math.round(min / 1440)} d ago`
}
