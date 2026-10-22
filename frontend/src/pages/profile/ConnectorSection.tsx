import { useEffect, useState, type ReactNode } from 'react'
import { api, type ClaudeStatus, type GithubStatus, type MailAccount } from '../../api'
import { BranchIcon, MailIcon, SparklesIcon } from '../../components/Icons'

function Group({ icon, name, note, action, children }: {
  icon: ReactNode
  name: string
  note: string
  action?: ReactNode
  children?: ReactNode
}) {
  return (
    <section className="conn__group">
      <header className="conn__head">
        <span className="conn__icon" aria-hidden>{icon}</span>
        <div className="conn__text">
          <h3>{name}</h3>
          <span className="muted">{note}</span>
        </div>
        {action}
      </header>
      <div className="conn__body">{children}</div>
    </section>
  )
}

/** Gmail accounts, as many as you like, each connected with an app password. Syncing them is in the Sync section. */
export function Gmail() {
  const [accounts, setAccounts] = useState<MailAccount[] | null>(null)
  const [adding, setAdding] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api.mailAccounts().then(setAccounts).catch(() => setAccounts([]))
  }, [])

  useEffect(() => {
    if (!adding) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) close()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const close = () => {
    setAdding(false)
    setEmail('')
    setPassword('')
    setError('')
  }

  const save = async () => {
    if (busy || !email.trim() || !password.trim()) return
    setBusy(true)
    setError('')
    try {
      const a = await api.addMailAccount(email, password)
      setAccounts((list) => [...(list ?? []).filter((x) => x.id !== a.id), a])
      close()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not connect that account.')
    } finally {
      setBusy(false)
    }
  }

  const remove = async (a: MailAccount) => {
    await api.removeMailAccount(a.id)
    setAccounts((list) => (list ?? []).filter((x) => x.id !== a.id))
  }

  return (
    <Group
      icon={<MailIcon />}
      name="Gmail"
      note="Find replies to your applications. You can connect more than one account."
      action={<button className="btn" title="Add account" aria-label="Add account" onClick={() => setAdding(true)}>+ Add</button>}
    >
      {accounts && accounts.length === 0 && <p className="conn__empty muted">No accounts connected.</p>}
      {accounts && accounts.length > 0 && (
        <ul className="conn__accounts">
          {accounts.map((a) => (
            <li key={a.id}>
              <span className="conn__dot" aria-hidden />
              <span className="conn__email">{a.email}</span>
              <button className="conn__remove" onClick={() => void remove(a)} title="Disconnect" aria-label={`Disconnect ${a.email}`}>&times;</button>
            </li>
          ))}
        </ul>
      )}
      {adding && (
        <div className="modal" onMouseDown={(e) => { if (e.target === e.currentTarget && !busy) close() }}>
          <form
            className="modal__card modal__card--small conn__modal"
            role="dialog"
            aria-label="Connect Gmail"
            onSubmit={(e) => { e.preventDefault(); void save() }}
          >
            <h2>Connect Gmail</h2>
            <input type="email" value={email} placeholder="you@gmail.com" autoComplete="off" disabled={busy} autoFocus onChange={(e) => setEmail(e.target.value)} />
            <input type="password" value={password} placeholder="App password (16 letters)" autoComplete="new-password" disabled={busy} onChange={(e) => setPassword(e.target.value)} />
            <p className="muted conn__help">
              Not your normal password. Turn on 2-step verification, then create one at{' '}
              <a href="https://myaccount.google.com/apppasswords" target="_blank" rel="noreferrer">myaccount.google.com/apppasswords</a>.
              It is checked with Gmail, then kept locked on this computer.
            </p>
            {error && <p className="conn__error">{error}</p>}
            <div className="conn__bar">
              <button type="button" className="btn" onClick={close} disabled={busy}>Cancel</button>
              <button type="submit" className="btn btn--primary" disabled={busy || !email.trim() || !password.trim()}>
                {busy ? 'Checking...' : 'Connect'}
              </button>
            </div>
          </form>
        </div>
      )}
    </Group>
  )
}

/** GitHub, connected with a personal access token. */
export function Github() {
  const [status, setStatus] = useState<GithubStatus | null>(null)
  const [open, setOpen] = useState(false)
  const [token, setToken] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    api.github().then(setStatus).catch(() => setStatus({ connected: false, login: null, connectedAt: null }))
  }, [])

  const close = () => {
    setOpen(false)
    setToken('')
    setError('')
  }

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) close()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  })

  const save = async () => {
    if (busy || !token.trim()) return
    setBusy(true)
    setError('')
    try {
      setStatus(await api.connectGithub(token))
      close()
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not connect GitHub.')
    } finally {
      setBusy(false)
    }
  }

  const disconnect = async () => {
    await api.disconnectGithub()
    setStatus({ connected: false, login: null, connectedAt: null })
  }

  return (
    <Group
      icon={<BranchIcon />}
      name="GitHub"
      note="Read your repositories to add projects, private ones too."
      action={<button className="btn" onClick={() => setOpen(true)}>{status?.connected ? 'Change token' : 'Connect'}</button>}
    >
      {status?.connected ? (
        <ul className="conn__accounts">
          <li>
            <span className="conn__dot" aria-hidden />
            <span className="conn__email">{status.login}</span>
            <button className="conn__remove" onClick={() => void disconnect()} title="Disconnect" aria-label="Disconnect GitHub">&times;</button>
          </li>
        </ul>
      ) : (
        <p className="conn__empty muted">Not connected.</p>
      )}
      {open && (
        <div className="modal" onMouseDown={(e) => { if (e.target === e.currentTarget && !busy) close() }}>
          <form
            className="modal__card modal__card--small conn__modal"
            role="dialog"
            aria-label="Connect GitHub"
            onSubmit={(e) => { e.preventDefault(); void save() }}
          >
            <h2>Connect GitHub</h2>
            <input type="password" value={token} placeholder="Personal access token" autoComplete="new-password" disabled={busy} autoFocus onChange={(e) => setToken(e.target.value)} />
            <p className="muted conn__help">
              Create a fine-grained token at{' '}
              <a href="https://github.com/settings/personal-access-tokens/new" target="_blank" rel="noreferrer">github.com/settings/personal-access-tokens</a>{' '}
              with read-only access to the repositories you want (Contents and Metadata). It is checked with GitHub, then kept locked on this computer.
            </p>
            {error && <p className="conn__error">{error}</p>}
            <div className="conn__bar">
              <button type="button" className="btn" onClick={close} disabled={busy}>Cancel</button>
              <button type="submit" className="btn btn--primary" disabled={busy || !token.trim()}>{busy ? 'Checking...' : 'Connect'}</button>
            </div>
          </form>
        </div>
      )}
    </Group>
  )
}

/** Claude: no key or password here. The app runs the `claude` program on this computer, so this shows which login that program has. */
export function Claude() {
  const [status, setStatus] = useState<ClaudeStatus | null>(null)
  const [asking, setAsking] = useState(false)
  const [waiting, setWaiting] = useState(false)
  const [error, setError] = useState('')

  const refresh = () => api.claude().then(setStatus).catch(() => setStatus({ installed: false, loggedIn: false, method: null, email: null }))

  useEffect(() => { void refresh() }, [])

  // after a sign-in is started, look again every few seconds until it is a subscription login (or five minutes pass)
  useEffect(() => {
    if (!waiting) return
    const started = Date.now()
    const timer = setInterval(async () => {
      try {
        const s = await api.claude()
        setStatus(s)
        if ((s.loggedIn && s.method === 'subscription') || Date.now() - started > 5 * 60_000) setWaiting(false)
      } catch {
        /* keep waiting */
      }
    }, 3000)
    return () => clearInterval(timer)
  }, [waiting])

  useEffect(() => {
    if (!asking) return
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') setAsking(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [asking])

  const signIn = async () => {
    setError('')
    try {
      await api.claudeLogin()
      setAsking(false)
      setWaiting(true)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not start the sign-in.')
    }
  }

  const onPlan = status?.loggedIn && status.method === 'subscription'
  return (
    <Group
      icon={<SparklesIcon />}
      name="Claude"
      note="The AI that reads job descriptions and your resume. It uses the login of the claude program on this computer."
      action={
        <button className="btn" disabled={waiting} onClick={() => setAsking(true)}>
          {waiting ? 'Waiting...' : onPlan ? 'Sign in again' : 'Sign in with Claude plan'}
        </button>
      }
    >
      {status && !status.installed && <p className="conn__error">The claude program was not found on this computer.</p>}
      {status?.installed && !status.loggedIn && <p className="conn__empty muted">Not signed in.</p>}
      {status?.loggedIn && (
        <ul className="conn__accounts">
          <li>
            <span className={onPlan ? 'conn__dot' : 'conn__dot conn__dot--warn'} aria-hidden />
            <span className="conn__email">{status.email ?? 'Signed in'}</span>
            <span className="muted conn__meta">{onPlan ? 'Claude plan' : 'API key, billed per use'}</span>
          </li>
        </ul>
      )}
      {status?.loggedIn && !onPlan && (
        <p className="conn__help muted">
          This login pays per use and can fail with &ldquo;credit balance is too low&rdquo;. Sign in with your Claude plan to use that instead.
        </p>
      )}
      {waiting && <p className="conn__help muted">Finish signing in in the browser window that opened. This updates by itself.</p>}
      {asking && (
        <div className="modal" onMouseDown={(e) => { if (e.target === e.currentTarget) setAsking(false) }}>
          <div className="modal__card modal__card--small conn__modal" role="dialog" aria-label="Sign in with Claude plan">
            <h2>Sign in with your Claude plan</h2>
            <p className="muted conn__help">
              This opens a sign-in page in your browser. It changes the login of the claude program on this computer, so
              everything that uses it, including Claude Code, switches to your plan.
            </p>
            {error && <p className="conn__error">{error}</p>}
            <div className="conn__bar">
              <button className="btn" onClick={() => setAsking(false)}>Cancel</button>
              <button className="btn btn--primary" onClick={() => void signIn()}>Open sign-in</button>
            </div>
          </div>
        </div>
      )}
    </Group>
  )
}

/** The accounts the app can connect to, each under its own heading. */
export default function ConnectorSection() {
  return (
    <div className="conn">
      <Github />
      <Claude />
      <Gmail />
    </div>
  )
}
