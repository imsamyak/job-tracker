import { useState } from 'react'
import { newId, type SkillGroup } from '../../profileStore'
import RewriteButton from './RewriteButton'
import type { SectionProps } from './types'

/** One group of skills: its name on top, the skills as tags (type one and press Enter or a comma to add it). */
function Group({ group, onChange, onRemove }: { group: SkillGroup; onChange: (g: SkillGroup) => void; onRemove: () => void }) {
  const [text, setText] = useState('')
  const [editing, setEditing] = useState<string | null>(null) // the skill being edited, by its current name
  const [draft, setDraft] = useState('')

  const add = (raw: string) => {
    const have = new Set(group.items.map((s) => s.toLowerCase()))
    const fresh = raw.split(',').map((s) => s.trim()).filter((s) => s && !have.has(s.toLowerCase()) && have.add(s.toLowerCase()))
    if (fresh.length > 0) onChange({ ...group, items: [...group.items, ...fresh] })
    setText('')
  }

  // an edited skill is renamed; emptied it is removed; renamed to one the group already has, the two become one
  const commit = (old: string) => {
    const name = draft.trim()
    setEditing(null)
    if (name === old) return
    const others = group.items.filter((x) => x !== old)
    const clash = others.some((x) => x.toLowerCase() === name.toLowerCase())
    const items = !name || clash ? others : group.items.map((x) => (x === old ? name : x))
    onChange({ ...group, items })
  }

  return (
    <div className="sgroup">
      <div className="sgroup__head">
        <input value={group.name} placeholder="Group name, for example Languages" aria-label="Group name" onChange={(e) => onChange({ ...group, name: e.target.value })} />
        <button className="conn__remove" title="Remove group" aria-label="Remove group" onClick={onRemove}>&times;</button>
      </div>
      {group.items.length > 0 && (
        <ul className="skills__list">
          {group.items.map((s) => (
            <li key={s} className="skills__tag">
              {editing === s ? (
                <input
                  className="skills__edit"
                  value={draft}
                  size={Math.max(4, draft.length)}
                  aria-label={`Edit ${s}`}
                  autoFocus
                  onChange={(e) => setDraft(e.target.value)}
                  onBlur={() => commit(s)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') e.currentTarget.blur()
                    if (e.key === 'Escape') {
                      setEditing(null)
                      e.stopPropagation()
                    }
                  }}
                />
              ) : (
                <button className="skills__label" title="Click to edit" onClick={() => { setEditing(s); setDraft(s) }}>{s}</button>
              )}
              <button aria-label={`Remove ${s}`} title="Remove" onClick={() => onChange({ ...group, items: group.items.filter((x) => x !== s) })}>&times;</button>
            </li>
          ))}
        </ul>
      )}
      <input
        className="skills__input"
        value={text}
        placeholder={group.items.length ? 'Add another skill' : 'Java, React, SQL...'}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault()
            add(text)
          }
        }}
        onBlur={() => add(text)}
      />
    </div>
  )
}

/** The groups as text for the AI, one per line: "Frontend: React, Redux". */
const toText = (groups: SkillGroup[]) => groups.filter((g) => g.items.length > 0).map((g) => `${g.name.trim() || 'Skills'}: ${g.items.join(', ')}`).join('\n')

/** The AI's answer back into groups. A group keeps its id when its name is unchanged, so nothing flickers. */
function fromText(text: string, old: SkillGroup[]): SkillGroup[] {
  return text.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
    const cut = line.indexOf(':')
    const name = cut > 0 ? line.slice(0, cut).trim() : 'Skills'
    const items = (cut > 0 ? line.slice(cut + 1) : line).split(',').map((s) => s.trim()).filter(Boolean)
    const same = old.find((g) => g.name.trim().toLowerCase() === name.toLowerCase())
    return { id: same?.id ?? newId(), name, items: [...new Set(items)] }
  }).filter((g) => g.items.length > 0)
}

/** Skills in groups, the way a resume lists them. */
export default function SkillsSection({ data, update }: SectionProps) {
  const set = (f: (groups: SkillGroup[]) => SkillGroup[]) => update((d) => ({ ...d, skills: f(d.skills) }))

  return (
    <div className="skillgroups">
      <div className="sgroup__rewrite">
        <RewriteButton
          kind="SKILLS"
          text={toText(data.skills)}
          context={data.personal.headline}
          hint="Optional: for example group them by area, or put backend skills first"
          onUse={(next) => set((gs) => fromText(next, gs))}
        />
      </div>
      {data.skills.map((g) => (
        <Group
          key={g.id}
          group={g}
          onChange={(next) => set((gs) => gs.map((x) => (x.id === g.id ? next : x)))}
          onRemove={() => set((gs) => gs.filter((x) => x.id !== g.id))}
        />
      ))}
      <div className="entries__add sgroup__add">
        <button className="btn" title="Add group" aria-label="Add group" onClick={() => set((gs) => [...gs, { id: newId(), name: '', items: [] }])}>+ Add</button>
      </div>
    </div>
  )
}
