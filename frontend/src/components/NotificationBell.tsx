import { useEffect, useState } from 'react'
import { api } from '../api'
import { BellIcon } from './Icons'
import { SEEN_KEY, readSeen } from '../pages/NotificationsPage'

/** The bell in the top bar: a badge counts the changes the mail sync made since you last opened the notifications. */
export default function NotificationBell({ on }: { on: boolean }) {
  const [count, setCount] = useState(0)

  useEffect(() => {
    let alive = true
    const look = async () => {
      try {
        const items = await api.notifications()
        const seen = readSeen()
        if (alive) setCount(items.filter((n) => Date.parse(n.createdAt) > seen).length)
      } catch {
        /* the badge just stays as it was */
      }
    }
    void look()
    const timer = setInterval(look, 30_000)
    const onSeen = () => setCount(0)
    window.addEventListener(SEEN_KEY, onSeen)
    return () => {
      alive = false
      clearInterval(timer)
      window.removeEventListener(SEEN_KEY, onSeen)
    }
  }, [])

  return (
    <a
      className={on ? 'header__profile header__profile--on bell' : 'header__profile bell'}
      href="#/notifications"
      title="Notifications"
      aria-label={count > 0 ? `Notifications, ${count} new` : 'Notifications'}
    >
      <BellIcon />
      {count > 0 && <span className="bell__badge">{count > 9 ? '9+' : count}</span>}
    </a>
  )
}
