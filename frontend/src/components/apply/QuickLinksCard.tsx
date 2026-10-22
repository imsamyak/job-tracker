import { useState } from 'react'
import { loadProfile, newId, saveProfile, type Labeled } from '../../profileStore'
import { CheckIcon, CopyIcon } from '../Icons'
import { useCopy } from './useCopy'

/**
 * The links you paste into application forms, copied in a click. They are the same links as the
 * Links section of your profile: add one here or there and it shows in both.
 */
export default function QuickLinksCard() {
  const [links, setLinks] = useState<Labeled[]>(() => loadProfile().contact.links)
  const [label, setLabel] = useState('')
  const [value, setValue] = useState('')
  const { copied, copy } = useCopy()

  // always change the latest saved profile, so nothing else in it is overwritten
  const save = (next: Labeled[]) => {
    setLinks(next)
    const p = loadProfile()
    saveProfile({ ...p, contact: { ...p.contact, links: next } })
  }

  const add = (e: React.FormEvent) => {
    e.preventDefault()
    if (!value.trim()) return
    save([...links, { id: newId(), label: label.trim(), value: value.trim() }])
    setLabel('')
    setValue('')
  }

  return (
    <section className="card acard">
      <header className="acard__head">
        <h2>Quick links</h2>
      </header>

      {links.length === 0 && <p className="muted small">Save your LinkedIn, GitHub or portfolio once and copy them in one click.</p>}

      <ul className="qlinks">
        {links.map((l) => (
          <li key={l.id}>
            <div className="qlinks__main">
              <div className="qlinks__label">{l.label || 'Link'}</div>
              <div className="qlinks__value">{l.value}</div>
            </div>
            <button className="iconbtn iconbtn--apply" title="Copy" aria-label={`Copy ${l.label || l.value}`} onClick={() => copy(l.id, l.value)}>
              {copied === l.id ? <CheckIcon /> : <CopyIcon />}
            </button>
            <button className="iconbtn iconbtn--delete" title="Remove" aria-label={`Remove ${l.label || l.value}`} onClick={() => save(links.filter((x) => x.id !== l.id))}>
              &times;
            </button>
          </li>
        ))}
      </ul>

      <form className="qadd" onSubmit={add}>
        <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder="Label" aria-label="Label" />
        <input value={value} onChange={(e) => setValue(e.target.value)} placeholder="Link" aria-label="Value" />
        <button className="btn" disabled={!value.trim()}>Add</button>
      </form>
    </section>
  )
}
