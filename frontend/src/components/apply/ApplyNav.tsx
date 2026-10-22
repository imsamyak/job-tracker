import type { ReactNode } from 'react'
import './ApplyNav.css'

export interface Section {
  id: string
  label: string
  icon: ReactNode
}

/**
 * The navigation bar of the apply window, on its right edge: one entry per section. Choosing one shows
 * that section on the left. Wide windows show icon and name; small ones show the icon alone.
 */
export default function ApplyNav({ sections, active, onSelect, pinLast }: {
  sections: Section[]
  active: string
  onSelect: (id: string) => void
  /** Stretch the bar to the bottom of the screen and park the last entry there. */
  pinLast?: boolean
}) {
  return (
    <nav className={pinLast ? 'anav anav--tall card' : 'anav card'} aria-label="Apply sections">
      {sections.map((s, i) => (
        <button
          key={s.id}
          title={s.label}
          aria-label={s.label}
          aria-current={active === s.id ? 'page' : undefined}
          className={(active === s.id ? 'anav__item anav__item--active' : 'anav__item') + (pinLast && i === sections.length - 1 ? ' anav__item--end' : '')}
          onClick={() => onSelect(s.id)}
        >
          {s.icon}
          <span className="anav__text">{s.label}</span>
        </button>
      ))}
    </nav>
  )
}
