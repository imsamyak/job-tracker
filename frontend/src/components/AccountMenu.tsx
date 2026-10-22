import { useEffect, useRef, useState } from 'react'
import { GearIcon, UserCircleIcon, UserIcon } from './Icons'
import './AccountMenu.css'

/** The round button at the right of the top bar. It opens a small menu: your profile, and settings. */
export default function AccountMenu({ route }: { route: 'profile' | 'settings' | null }) {
  const [open, setOpen] = useState(false)
  const box = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const onDown = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false)
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    window.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      window.removeEventListener('keydown', onKey)
    }
  }, [open])

  // choosing a page, or the route changing some other way, closes the menu
  useEffect(() => setOpen(false), [route])

  return (
    <div className="acct" ref={box}>
      <button
        className={route || open ? 'header__profile header__profile--on' : 'header__profile'}
        title="Account"
        aria-label="Account menu"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        <UserCircleIcon />
      </button>
      {open && (
        <div className="acct__menu" role="menu">
          <a role="menuitem" href="#/profile" className={route === 'profile' ? 'acct__item acct__item--on' : 'acct__item'} onClick={() => setOpen(false)}>
            <UserIcon /> Profile
          </a>
          <a role="menuitem" href="#/settings" className={route === 'settings' ? 'acct__item acct__item--on' : 'acct__item'} onClick={() => setOpen(false)}>
            <GearIcon /> Settings
          </a>
        </div>
      )}
    </div>
  )
}
