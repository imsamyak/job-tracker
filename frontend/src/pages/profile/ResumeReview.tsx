import { useEffect, useMemo, useState } from 'react'
import type { ExtractedProfile } from '../../api'
import type { ProfileData } from '../../profileStore'
import { applyResume, counts, defaultChoices, FIELDS, type Choice, type ListKey } from '../../resumeMerge'

const LISTS: { key: ListKey; label: string }[] = [
  { key: 'experience', label: 'Experience' },
  { key: 'projects', label: 'Projects' },
  { key: 'skills', label: 'Skills' },
  { key: 'emails', label: 'Emails' },
  { key: 'phones', label: 'Phone numbers' },
  { key: 'links', label: 'Links' },
]

/** Another resume was read and your profile already has details: for each part, merge it in, replace what you have, or ignore it. */
export default function ResumeReview({ current, incoming, onApply, onCancel }: {
  current: ProfileData
  incoming: ExtractedProfile
  onApply: (next: ProfileData) => void
  onCancel: () => void
}) {
  const [choices, setChoices] = useState(() => defaultChoices(current, incoming))
  const n = useMemo(() => counts(current, incoming), [current, incoming])
  const changed = FIELDS.filter((f) => incoming.personal[f.key].trim() && incoming.personal[f.key].trim() !== current.personal[f.key].trim())

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onCancel()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onCancel])

  const setList = (k: ListKey, c: Choice) => setChoices((s) => ({ ...s, lists: { ...s.lists, [k]: c } }))

  return (
    <div className="modal" onMouseDown={(e) => { if (e.target === e.currentTarget) onCancel() }}>
      <div className="modal__card review" role="dialog" aria-label="Update profile from resume">
        <h2>Update your profile from this resume</h2>
        <p className="muted">Your profile already has details. For each part, choose what to do with what the new resume says.</p>

        {changed.length > 0 && (
          <section>
            <h3>Personal details</h3>
            {changed.map((f) => (
              <div key={f.key} className="review__row review__row--field">
                <strong>{f.label}</strong>
                <span className="muted review__old" title={current.personal[f.key]}>{current.personal[f.key] || 'empty'}</span>
                <span className="review__arrow" aria-hidden>&rarr;</span>
                <span className="review__new" title={incoming.personal[f.key]}>{incoming.personal[f.key]}</span>
                <span className="seg" role="group" aria-label={f.label}>
                  {(['keep', 'new'] as const).map((v) => (
                    <button key={v} className={choices.personal[f.key] === v ? 'seg__on' : ''}
                            onClick={() => setChoices((s) => ({ ...s, personal: { ...s.personal, [f.key]: v } }))}>
                      {v === 'keep' ? 'Keep mine' : 'Use new'}
                    </button>
                  ))}
                </span>
              </div>
            ))}
          </section>
        )}

        <section>
          <h3>Lists</h3>
          {LISTS.filter((l) => n[l.key].incoming > 0).map((l) => (
            <div key={l.key} className="review__row">
              <strong>{l.label}</strong>
              <span className="muted">{n[l.key].fresh} new of {n[l.key].incoming} in the resume</span>
              <span className="seg" role="group" aria-label={l.label}>
                {([['merge', 'Merge'], ['replace', 'Replace'], ['ignore', 'Ignore']] as [Choice, string][]).map(([v, label]) => (
                  <button key={v} className={choices.lists[l.key] === v ? 'seg__on' : ''} onClick={() => setList(l.key, v)}>{label}</button>
                ))}
              </span>
            </div>
          ))}
          <p className="muted review__hint">Merge adds only what is new. Replace swaps your list for the resume&rsquo;s. Ignore leaves yours alone.</p>
        </section>

        <div className="review__bar">
          <button className="btn" onClick={onCancel}>Cancel</button>
          <button className="btn btn--primary" onClick={() => onApply(applyResume(current, incoming, choices))}>Apply</button>
        </div>
      </div>
    </div>
  )
}
