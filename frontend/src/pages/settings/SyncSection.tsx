import { useEffect, useRef, useState } from 'react'
import { api, statusLabel, type MailAccount, type MailSyncState } from '../../api'
import { RefreshIcon } from '../../components/Icons'
import { ago } from '../../timeAgo'

/** yyyy-mm-dd in local time, as the sync endpoint wants it. */
const dateKey = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
const daysAgo = (n: number) => dateKey(new Date(Date.now() - n * 86_400_000))

/** How far back Sync can read: never more than 3 months, as older mail is ignored. The automatic sync only ever looks at new mail. */
const RANGES = [
  { id: 'week', label: 'Past week', from: () => daysAgo(7) },
  { id: 'month', label: 'Past month', from: () => daysAgo(30) },
  { id: 'quarter', label: 'Past 3 months', from: () => daysAgo(92) },
]

/** What Claude decided about an email, in plain words. */
const OUTCOME: Record<string, string> = {
  REJECTED: 'Rejection',
  IN_TALKS: 'Moving forward',
  APPLIED: 'Application received',
  NONE: 'Nothing about an application',
}

/** One account's Sync button: choose how far back to read, then it reads that account's mail again from there. */
function SyncButton({ email, disabled, syncing, onPick }: {
  email: string
  disabled: boolean
  syncing: boolean
  onPick: (from: string) => void
}) {
  const [picking, setPicking] = useState(false)
  const box = useRef<HTMLSpanElement>(null)

  // the range menu closes on a click elsewhere or Escape
  useEffect(() => {
    if (!picking) return
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setPicking(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setPicking(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [picking])

  return (
    <span className="conn__range" ref={box}>
      <button
        className="btn conn__sync"
        title="Read this account's mail again from a past date. Otherwise only new mail is checked, automatically."
        aria-label={`Sync ${email} again`}
        aria-haspopup="menu"
        aria-expanded={picking}
        disabled={disabled}
        onClick={() => setPicking((p) => !p)}
      >
        <span className={syncing ? 'spin' : undefined}><RefreshIcon /></span> {syncing ? 'Syncing...' : 'Sync'}
      </button>
      {picking && (
        <div className="conn__rangemenu" role="menu">
          <p className="muted">Read mail from</p>
          {RANGES.map((r) => (
            <button key={r.id} role="menuitem" onClick={() => { setPicking(false); onPick(r.from()) }}>{r.label}</button>
          ))}
        </div>
      )}
    </span>
  )
}

/**
 * Keeping your jobs in step with your mail. New mail is checked by itself every few minutes; here each connected Gmail
 * has a Sync button that reads that account's mail again from a week, a month or 3 months back, and below
 * it you can see what the last check did and which jobs it moved.
 */
export default function SyncSection() {
  const [accounts, setAccounts] = useState<MailAccount[] | null>(null)
  const [sync, setSync] = useState<MailSyncState | null>(null)
  const [syncingId, setSyncingId] = useState<number | null>(null)
  const [last, setLast] = useState<{ account: MailAccount; from: string } | null>(null) // what was asked, so "read more" continues it
  const running = sync?.running ?? false

  useEffect(() => {
    api.mailAccounts().then(setAccounts).catch(() => setAccounts([]))
    api.mailSync().then(setSync).catch(() => undefined)
  }, [])

  // while a sync runs, look again every few seconds; when it ends, show what it found
  useEffect(() => {
    if (!running) return
    const timer = setInterval(async () => {
      try {
        const s = await api.mailSync()
        setSync(s)
        if (!s.running) {
          setSyncingId(null)
          setAccounts(await api.mailAccounts())
        }
      } catch {
        /* keep waiting */
      }
    }, 2500)
    return () => clearInterval(timer)
  }, [running])

  const syncNow = async (account: MailAccount, from: string) => {
    setSyncingId(account.id)
    setLast({ account, from })
    try {
      await api.syncMailNow(account.id, from)
      setSync((s) => ({ running: true, lastRunAt: s?.lastRunAt ?? null, lastRun: s?.lastRun ?? null, progress: null, recent: s?.recent ?? [], checked: s?.checked ?? [] }))
    } catch {
      setSyncingId(null) // the next look shows the state
    }
  }

  const p = sync?.progress
  const reading = p && p.total > 0 ? `Reading ${p.done.toLocaleString()} of ${p.total.toLocaleString()} emails` : 'Reading...'

  if (accounts && accounts.length === 0) {
    return <p className="muted">Connect a Gmail account under Connectors first, then it shows up here.</p>
  }

  return (
    <div className="sync">
      <ul className="conn__accounts">
        {(accounts ?? []).map((a) => (
          <li key={a.id}>
            <span className={a.lastError ? 'conn__dot conn__dot--warn' : 'conn__dot'} aria-hidden />
            <span className="conn__email">{a.email}</span>
            <span className={a.lastError ? 'conn__meta conn__meta--bad' : 'conn__meta muted'} title={a.lastError ?? undefined}>
              {a.lastError ? 'Could not read' : running && syncingId === a.id ? reading : `Checked ${ago(a.lastSyncedAt)}`}
            </span>
            <SyncButton email={a.email} disabled={running} syncing={running && syncingId === a.id} onPick={(from) => void syncNow(a, from)} />
          </li>
        ))}
      </ul>

      {accounts?.some((a) => a.lastError) && <p className="conn__error">{accounts.find((a) => a.lastError)?.lastError}</p>}

      <p className="conn__help muted">
        New mail is checked automatically every few minutes. When an email says you were rejected or invited to talk, that job&rsquo;s
        status updates by itself. Only emails that look like they are about an application are ever read by Claude. Applications not in your list yet are added.
      </p>

      {sync?.lastRun && !running && (
        <p className="conn__help muted">
          Last check: {sync.lastRun.read} emails read, {sync.lastRun.matched} about applications, {sync.lastRun.added} added, {sync.lastRun.changed} updated.
        </p>
      )}

      {sync?.lastRun?.more && !running && last && (
        <div className="sync__more">
          <span>Claude has read 100 emails so far, and there are more.</span>
          <button className="btn btn--primary" onClick={() => void syncNow(last.account, last.from)}>Read 100 more</button>
        </div>
      )}
      {sync && sync.checked.length > 0 && (
        <details className="sync__checked">
          <summary>Emails checked ({sync.checked.length})</summary>
          <ul>
            {sync.checked.map((c, i) => (
              <li key={i}>
                <span className="sync__subject" title={c.sender ?? undefined}>{c.subject || '(no subject)'}</span>
                <span className="muted">{OUTCOME[c.outcome ?? ''] ?? c.outcome}{c.company ? ` · ${c.company}` : ''}</span>
                {c.reason && <span className="muted sync__reason">{c.reason}</span>}
              </li>
            ))}
          </ul>
        </details>
      )}
      {sync && sync.recent.length > 0 && (
        <ul className="conn__updates">
          {sync.recent.slice(0, 5).map((u, i) => (
            <li key={i}>
              <a href={u.jobId ? `#/jobs/${u.jobId}` : undefined}>{u.company ?? 'A job'}</a>
              <span className={`status status--${u.status.toLowerCase()}`}>{statusLabel(u.status)}</span>
              <span className="muted conn__subject" title={u.subject ?? undefined}>{u.subject}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
