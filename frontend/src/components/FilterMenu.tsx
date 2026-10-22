import { useEffect, useState } from 'react'
import { STATUSES } from '../api'
import { activeCount, NO_FILTERS, type Filters, type Since } from '../filters'
import { FilterIcon } from './Icons'
import './FilterMenu.css'

const SINCE: { id: Since; label: string }[] = [
  { id: 'any', label: 'Any time' },
  { id: '7', label: 'Last 7 days' },
  { id: '30', label: 'Last 30 days' },
]

function toggle<T>(list: T[], v: T): T[] {
  return list.includes(v) ? list.filter((x) => x !== v) : [...list, v]
}

/** The Filter button. It opens a side panel; nothing changes in the list until Apply is pressed. */
export default function FilterMenu({ value, onChange, locations }: {
  value: Filters
  onChange: (f: Filters) => void
  locations: string[]
}) {
  const [open, setOpen] = useState(false)
  const count = activeCount(value)

  return (
    <>
      <button
        className={count > 0 ? 'btn btn--filtered' : 'btn'}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => setOpen(true)}
      >
        <FilterIcon /> Filter
        {count > 0 && <span className="filter__count">{count}</span>}
      </button>

      {open && (
        <Drawer
          initial={value}
          locations={locations}
          onApply={(f) => { onChange(f); setOpen(false) }}
          onCancel={() => setOpen(false)}
        />
      )}
    </>
  )
}

/**
 * Slides in from the left, below the top bar (which stays visible). It edits its own copy of the
 * filters, so Cancel, Esc or a click outside simply throw the changes away.
 */
function Drawer({ initial, locations, onApply, onCancel }: {
  initial: Filters
  locations: string[]
  onApply: (f: Filters) => void
  onCancel: () => void
}) {
  const [draft, setDraft] = useState<Filters>(initial)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onCancel])

  return (
    <>
      <div className="fdrawer__backdrop" onMouseDown={onCancel} />
      <aside className="fdrawer" role="dialog" aria-label="Filters">
        <header className="fdrawer__head">
          <h2>Filters</h2>
          <button className="link" disabled={activeCount(draft) === 0} onClick={() => setDraft(NO_FILTERS)}>Clear all</button>
        </header>

        <div className="fdrawer__body">
          <section>
            <h3>Status</h3>
            {STATUSES.map((s) => (
              <label key={s.id} className="opt">
                <input
                  type="checkbox"
                  checked={draft.statuses.includes(s.id)}
                  onChange={() => setDraft({ ...draft, statuses: toggle(draft.statuses, s.id) })}
                />
                <span className={`status status--${s.id.toLowerCase()}`}>{s.label}</span>
              </label>
            ))}
          </section>

          {locations.length > 0 && (
            <section>
              <h3>Location</h3>
              {locations.map((l) => (
                <label key={l} className="opt">
                  <input
                    type="checkbox"
                    checked={draft.locations.includes(l)}
                    onChange={() => setDraft({ ...draft, locations: toggle(draft.locations, l) })}
                  />
                  <span>{l}</span>
                </label>
              ))}
            </section>
          )}

          <section>
            <h3>Added</h3>
            {SINCE.map((s) => (
              <label key={s.id} className="opt">
                <input
                  type="radio"
                  name="since"
                  checked={draft.since === s.id}
                  onChange={() => setDraft({ ...draft, since: s.id })}
                />
                <span>{s.label}</span>
              </label>
            ))}
          </section>
        </div>

        <footer className="fdrawer__foot">
          <button className="btn" onClick={onCancel}>Cancel</button>
          <button className="btn btn--primary" onClick={() => onApply(draft)}>Apply filters</button>
        </footer>
      </aside>
    </>
  )
}
