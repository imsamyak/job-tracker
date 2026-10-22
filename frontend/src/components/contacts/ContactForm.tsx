import { useRef, useState } from 'react'
import { api, type Contact } from '../../api'
import { LinkIcon, MailIcon, PhoneIcon } from '../Icons'
import './contacts.css'

const URL_LIKE = /^(https?:\/\/|www\.|[a-z0-9-]+(\.[a-z0-9-]+)*\.[a-z]{2,}\/)/i
const PHONE_LIKE = /^[+\d][\d\s().-]{4,}$/

/** What was typed in the Contact field: shown as a link to open, a mail link, a dial link, or plain text. */
export function ContactLink({ value }: { value: string }) {
  const v = value.trim()
  if (v.includes('@') && !URL_LIKE.test(v)) return <a href={`mailto:${v}`}><MailIcon /> {v}</a>
  if (URL_LIKE.test(v)) {
    const href = /^https?:\/\//i.test(v) ? v : `https://${v}`
    return <a href={href} target="_blank" rel="noreferrer"><LinkIcon /> {v}</a>
  }
  if (PHONE_LIKE.test(v)) return <a href={`tel:${v.replace(/[^\d+]/g, '')}`}><PhoneIcon /> {v}</a>
  return <span>{v}</span>
}

/**
 * Name, role and contact. Adding is one input row with Clear and Save below it (shown once you start
 * typing; it clears itself after saving, ready for the next one); editing a saved contact has
 * Cancel and Delete instead.
 */
export function ContactForm({ jobId, contact, onSaved, onCancel, focusOnOpen }: {
  jobId: number
  contact: Contact | null
  onSaved: () => void
  onCancel: () => void
  focusOnOpen?: boolean
}) {
  const [name, setName] = useState(contact?.name ?? '')
  const [role, setRole] = useState(contact?.role ?? '')
  const [info, setInfo] = useState(contact?.contact ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirming, setConfirming] = useState(false)
  const nameInput = useRef<HTMLInputElement>(null)
  const adding = contact === null
  const typing = name !== '' || role !== '' || info !== ''

  const clear = () => {
    setName('')
    setRole('')
    setInfo('')
    setError(null)
    nameInput.current?.focus()
  }

  const run = async (action: () => Promise<unknown>) => {
    setBusy(true)
    setError(null)
    try {
      await action()
      if (adding) {
        // ready for the next contact
        setName('')
        setRole('')
        setInfo('')
        setBusy(false)
        nameInput.current?.focus()
      }
      onSaved()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
      setConfirming(false)
    }
  }

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    const input = { name, role, contact: info }
    run(() => (contact ? api.updateContact(contact.id, input) : api.addContact(jobId, input)))
  }

  const saveButton = (
    <button className="btn btn--primary" disabled={busy || !name.trim()} tabIndex={adding && !typing ? -1 : 0}>{busy ? 'Saving...' : 'Save'}</button>
  )

  return (
    <form className="cf" onSubmit={submit}>
      {error && <div className="alert">{error}</div>}
      <div className="cf__row">
        <input ref={nameInput} value={name} onChange={(e) => setName(e.target.value)} placeholder="Name" aria-label="Name"
               required autoFocus={focusOnOpen || !adding} />
        <input value={role} onChange={(e) => setRole(e.target.value)} placeholder="Role" aria-label="Role" />
        <input value={info} onChange={(e) => setInfo(e.target.value)} placeholder="Contact" aria-label="Contact" />
      </div>

      {adding && (
        // Always in the layout so nothing jumps; only visible (and clickable) once something is typed.
        <div className={typing ? 'cf__actions' : 'cf__actions cf__actions--hidden'}>
          <button type="button" className="btn" onClick={clear} disabled={busy} tabIndex={typing ? 0 : -1}>Clear</button>
          {saveButton}
        </div>
      )}

      {!adding && (confirming ? (
        <div className="cf__bar cf__bar--confirm">
          <span>Delete this contact?</span>
          <button type="button" className="btn" onClick={() => setConfirming(false)} disabled={busy}>Cancel</button>
          <button type="button" className="btn btn--primary btn--red" disabled={busy}
                  onClick={() => contact && run(() => api.deleteContact(contact.id))}>
            {busy ? 'Deleting...' : 'Delete'}
          </button>
        </div>
      ) : (
        <div className="cf__bar">
          <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)} disabled={busy}>Delete</button>
          <span className="cf__spacer" />
          <button type="button" className="btn" onClick={onCancel} disabled={busy}>Cancel</button>
          {saveButton}
        </div>
      ))}
    </form>
  )
}
