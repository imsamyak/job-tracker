import { useState } from 'react'
import { newId, type Labeled } from '../../profileStore'

/** A list of "label: value" entries (emails, phone numbers, links) with a row to add another. */
export default function LabeledList({ title, labelHint, valueHint, items, onChange, showTitle = true }: {
  title: string
  labelHint: string
  valueHint: string
  items: Labeled[]
  onChange: (next: Labeled[]) => void
  showTitle?: boolean
}) {
  const [label, setLabel] = useState('')
  const [value, setValue] = useState('')

  const add = (e: React.FormEvent) => {
    e.preventDefault()
    if (!value.trim()) return
    onChange([...items, { id: newId(), label: label.trim(), value: value.trim() }])
    setLabel('')
    setValue('')
  }

  return (
    <div className="cgroup">
      {showTitle && <h3>{title}</h3>}
      {items.length === 0 && <p className="muted small">None yet.</p>}
      <ul className="cgroup__list">
        {items.map((i) => (
          <li key={i.id}>
            <span className="cgroup__label">{i.label || '—'}</span>
            <span className="cgroup__value">{i.value}</span>
            <button className="iconbtn iconbtn--delete" title="Remove" aria-label={`Remove ${i.value}`} onClick={() => onChange(items.filter((x) => x.id !== i.id))}>&times;</button>
          </li>
        ))}
      </ul>
      <form className="cgroup__add" onSubmit={add}>
        <input value={label} onChange={(e) => setLabel(e.target.value)} placeholder={labelHint} aria-label={`${title}: label`} />
        <input value={value} onChange={(e) => setValue(e.target.value)} placeholder={valueHint} aria-label={`${title}: value`} />
        <button className="btn" disabled={!value.trim()}>Add</button>
      </form>
    </div>
  )
}
