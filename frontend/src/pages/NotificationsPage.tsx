import { useEffect, useState } from 'react'
import { api, statusLabel, type Notification } from '../api'
import { gmailLink } from '../gmail'
import { ago } from '../timeAgo'
import './NotificationsPage.css'

/** The browser remembers when you last looked, so the bell can count what is new. */
export const SEEN_KEY = 'jobtracker.notifications.seen'

export function readSeen(): number {
  try {
    return Number(localStorage.getItem(SEEN_KEY)) || 0
  } catch {
    return 0
  }
}

/** What the mail sync did lately, newest first: jobs it added and jobs whose status it changed, with the email behind each. */
export default function NotificationsPage() {
  const [items, setItems] = useState<Notification[] | null>(null)
  const [error, setError] = useState('')
  const [seenBefore] = useState(readSeen) // what was new when this page opened stays marked while you read it

  useEffect(() => {
    api.notifications().then(setItems).catch((e) => setError(e instanceof Error ? e.message : String(e)))
    try {
      localStorage.setItem(SEEN_KEY, String(Date.now()))
    } catch {
      /* the badge then comes back; nothing is lost */
    }
    window.dispatchEvent(new Event(SEEN_KEY))
  }, [])

  return (
    <div className="notes">
      <a className="back" href="#/">&larr; All jobs</a>
      <h1>Notifications</h1>
      <p className="muted">Jobs your Gmail sync added or moved, newest first.</p>
      {error && <div className="alert">{error}</div>}
      {items && items.length === 0 && (
        <div className="card notes__empty">Nothing yet. When an email says you applied, were shortlisted or were rejected, it shows up here.</div>
      )}
      <ul className="notes__list">
        {(items ?? []).map((n) => (
          <li key={n.id} className="card note">
            <div className="note__head">
              <strong>{n.company ?? 'A job'}</strong>
              {n.role && <span className="muted">{n.role}</span>}
              <span className={`status status--${n.status.toLowerCase()}`}>{statusLabel(n.status)}</span>
              {Date.parse(n.createdAt) > seenBefore && <span className="note__new">New</span>}
              <span className="note__time muted">{ago(n.createdAt)}</span>
            </div>
            <p className="note__what">
              {n.kind === 'ADDED' ? `Added from your Gmail as ${statusLabel(n.status)}.` : `Status changed to ${statusLabel(n.status)} because of an email.`}
              {n.reason ? ` ${n.reason}` : ''}
            </p>
            <p className="note__mail muted" title={n.sender ?? undefined}>{n.platform ? `${n.platform} · ` : ''}{n.subject || '(no subject)'}</p>
            <div className="note__actions">
              {n.jobId && <a className="btn" href={`#/jobs/${n.jobId}`}>View job</a>}
              <a className="btn" href={gmailLink(n.mailId, n.mailAccount)} target="_blank" rel="noreferrer">View mail</a>
            </div>
          </li>
        ))}
      </ul>
    </div>
  )
}
