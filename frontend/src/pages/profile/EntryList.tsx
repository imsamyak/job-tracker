import { Fragment, useState, type ReactNode } from 'react'
import GrowTextarea from './GrowTextarea'
import RewriteButton from './RewriteButton'
import { newId } from '../../profileStore'
import { PencilIcon } from '../../components/Icons'

export interface FieldDef<T> {
  key: keyof T & string
  label: string
  type?: 'text' | 'textarea' | 'check' | 'lines'
  placeholder?: string
  /** Two of these sit side by side. */
  half?: boolean
  /** Only shown while this is true (for example the end date, until "I work here now" is ticked). */
  showIf?: (v: T) => boolean
  /** Adds a "Rewrite with AI" button under this field. context says what the entry is about. */
  rewrite?: { kind: 'EXPERIENCE' | 'PROJECT'; context: (v: T) => string }
}

/**
 * A list of entries (jobs, projects...) with add, edit and delete. Fields are described, not
 * hand-written, so each section only says what its entries look like.
 */
export default function EntryList<T extends { id: string }>({ items, onChange, fields, blank, summary, addLabel, addTitle, empty, requiredKey, helper }: {
  items: T[]
  onChange: (next: T[]) => void
  fields: FieldDef<T>[]
  blank: () => Omit<T, 'id'>
  summary: (item: T) => { title: string; subtitle?: string; body?: string; links?: string[] }
  addLabel: string
  /** What the add button does, for screen readers and the tooltip. */
  addTitle: string
  empty: string
  /** The field that must be filled in before an entry can be saved. */
  requiredKey: keyof T & string
  /** Shown above the fields of a new entry, to fill them in some other way (for example from a link). */
  helper?: (draft: T, setDraft: (next: T) => void) => ReactNode
}) {
  const [editing, setEditing] = useState<string | null>(null) // an entry's id, or 'new'
  const [draft, setDraft] = useState<T | null>(null)
  const [confirming, setConfirming] = useState(false)

  const open = (item: T | null) => {
    setConfirming(false)
    setEditing(item ? item.id : 'new')
    setDraft(item ?? ({ id: newId(), ...blank() } as T))
  }
  const close = () => { setEditing(null); setDraft(null); setConfirming(false) }

  const save = (e: React.FormEvent) => {
    e.preventDefault()
    if (!draft) return
    // a list typed one per line: drop the empty lines now that typing is done
    const clean = { ...draft } as Record<string, unknown>
    for (const f of fields) if (f.type === 'lines') clean[f.key] = ((draft[f.key] as unknown as string[]) ?? []).map((l) => l.trim()).filter(Boolean)
    const saved = clean as unknown as T
    onChange(editing === 'new' ? [...items, saved] : items.map((i) => (i.id === saved.id ? saved : i)))
    close()
  }

  const form = draft && (
    <form className="entry__form" onSubmit={save}>
      {editing === 'new' && helper?.(draft, setDraft)}
      <div className="entry__grid">
        {fields.filter((f) => !f.showIf || f.showIf(draft)).map((f) => (
          <Fragment key={f.key}>
          <label className={`${f.half ? 'entry__half' : 'entry__full'}${f.type === 'check' ? ' entry__check' : ''}`}>
            {f.type === 'check' ? (
              <>
                <input type="checkbox" checked={Boolean(draft[f.key])} onChange={(e) => setDraft({ ...draft, [f.key]: e.target.checked })} />
                {f.label}
              </>
            ) : (
              <>
                {f.label}
                {f.type === 'lines' ? (
                  <GrowTextarea rows={3} value={((draft[f.key] as unknown as string[]) ?? []).join('\n')} placeholder={f.placeholder}
                            onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value.split('\n') })} />
                ) : f.type === 'textarea' ? (
                  <GrowTextarea rows={4} value={String(draft[f.key] ?? '')} placeholder={f.placeholder} onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })} />
                ) : (
                  <input value={String(draft[f.key] ?? '')} placeholder={f.placeholder} onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })} />
                )}
              </>
            )}
          </label>
          {f.rewrite && (
            <div className="entry__full">
              <RewriteButton kind={f.rewrite.kind} text={String(draft[f.key] ?? '')} context={f.rewrite.context(draft)}
                             onUse={(next) => setDraft({ ...draft, [f.key]: next })} />
            </div>
          )}
          </Fragment>
        ))}
      </div>
      {confirming ? (
        <div className="entry__bar entry__bar--confirm">
          <span>Delete this entry?</span>
          <button type="button" className="btn" onClick={() => setConfirming(false)}>Cancel</button>
          <button type="button" className="btn btn--primary btn--red" onClick={() => { onChange(items.filter((i) => i.id !== draft.id)); close() }}>Delete</button>
        </div>
      ) : (
        <div className="entry__bar">
          {editing !== 'new' && <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)}>Delete</button>}
          <span className="entry__spacer" />
          <button type="button" className="btn" onClick={close}>Cancel</button>
          <button className="btn btn--primary" disabled={!String(draft[requiredKey] ?? '').trim()}>Save</button>
        </div>
      )}
    </form>
  )

  return (
    <div className="entries">
      {items.length === 0 && editing !== 'new' && <p className="muted">{empty}</p>}

      <ul className="entry__list">
        {items.map((item) => {
          const s = summary(item)
          return (
            <li key={item.id} className="entry">
              {editing === item.id ? form : (
                <>
                  <div className="entry__main">
                    <div className="entry__title">{s.title}</div>
                    {s.subtitle && <div className="entry__sub">{s.subtitle}</div>}
                    {s.links && s.links.length > 0 && (
                      <div className="entry__links">
                        {s.links.map((l) => <a key={l} href={/^https?:\/\//.test(l) ? l : `https://${l}`} target="_blank" rel="noreferrer">{l.replace(/^https?:\/\/(www\.)?/, '')}</a>)}
                      </div>
                    )}
                    {s.body && <div className="entry__body">{s.body}</div>}
                  </div>
                  <button className="iconbtn iconbtn--apply" title="Edit" aria-label={`Edit ${s.title}`} onClick={() => open(item)}><PencilIcon /></button>
                </>
              )}
            </li>
          )
        })}
        {editing === 'new' && <li className="entry">{form}</li>}
      </ul>

      {editing !== 'new' && (
        <div className="entries__add">
          <button className="btn" title={addTitle} aria-label={addTitle} onClick={() => open(null)}>{addLabel}</button>
        </div>
      )}
    </div>
  )
}

