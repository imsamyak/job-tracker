import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { STATUSES, type Status } from '../api'
import { RefreshIcon } from './Icons'
import './StatusMenu.css'

const GAP = 6 // space between the button and the menu
const EDGE = 8 // never closer than this to the edge of the window

/**
 * Icon button that opens a small menu of the other statuses (compact: smaller, for table rows).
 * The menu is placed on screen rather than inside its parent, so a clipping table or card cannot cut
 * it off, and it opens upward when there is not enough room below the button.
 */
export default function StatusMenu({ current, onPick, compact }: {
  current: Status
  onPick: (s: Status) => void
  compact?: boolean
}) {
  // where the button is, in window coordinates; null = closed
  const [anchor, setAnchor] = useState<{ top: number; bottom: number; right: number } | null>(null)
  const [top, setTop] = useState(0)
  const box = useRef<HTMLDivElement>(null)
  const list = useRef<HTMLUListElement>(null)
  const open = anchor !== null

  // Once the menu exists we know its height: put it below the button, or above if that would overflow.
  useLayoutEffect(() => {
    if (!anchor || !list.current) return
    const h = list.current.offsetHeight
    const below = anchor.bottom + GAP
    const above = anchor.top - GAP - h
    const fitsBelow = below + h <= window.innerHeight - EDGE
    const y = fitsBelow ? below : above >= EDGE ? above : Math.max(EDGE, window.innerHeight - h - EDGE)
    setTop(y)
  }, [anchor])

  useEffect(() => {
    if (!open) return
    const close = () => setAnchor(null)
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) close()
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') close()
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', close)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', close)
    }
  }, [open])

  const toggle = (e: React.MouseEvent<HTMLButtonElement>) => {
    if (open) return setAnchor(null)
    const r = e.currentTarget.getBoundingClientRect()
    setTop(r.bottom + GAP)
    setAnchor({ top: r.top, bottom: r.bottom, right: window.innerWidth - r.right })
  }

  return (
    // stopPropagation: inside a clickable table row, using the menu must not also open the row
    <div className="menu" ref={box} onClick={(e) => e.stopPropagation()}>
      {compact ? (
        <button className="iconbtn iconbtn--update" title="Update status" aria-label="Update status"
                aria-haspopup="menu" aria-expanded={open} onClick={toggle}>
          <RefreshIcon />
        </button>
      ) : (
        <button className="btn btn--icon" title="Update status" aria-label="Update status"
                aria-haspopup="menu" aria-expanded={open} onClick={toggle}>
          <RefreshIcon />
        </button>
      )}
      {anchor && (
        <ul className="menu__list" role="menu" ref={list} style={{ top, right: anchor.right }}>
          {STATUSES.filter((s) => s.id !== current).map((s) => (
            <li key={s.id} role="none" className={`status--${s.id.toLowerCase()}`}>
              <button
                role="menuitem"
                className="menu__item"
                onClick={() => { setAnchor(null); onPick(s.id) }}
              >
                <span className="dot" />
                {s.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
