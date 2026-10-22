import { useEffect, useState } from 'react'
import './App.css'
import Footer from './components/Footer'
import ExtensionApp from './pages/extension/ExtensionApp'
import Applications from './pages/Applications'
import ApplyPage from './pages/ApplyPage'
import AccountMenu from './components/AccountMenu'
import AgentChat from './components/AgentChat'
import NotificationBell from './components/NotificationBell'
import NotificationsPage from './pages/NotificationsPage'
import JobPage from './pages/JobPage'
import ProfilePage from './pages/ProfilePage'
import SettingsPage from './pages/SettingsPage'
import './mobile.css'

/** Tiny hash router: "#/" is the list, "#/jobs/5" is one job, "#/jobs/5/apply" is its apply page. */
function useRoute() {
  const [hash, setHash] = useState(window.location.hash)
  useEffect(() => {
    const on = () => { setHash(window.location.hash); window.scrollTo(0, 0) }
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  return hash
}

export default function App() {
  const hash = useRoute()
  const match = /^#\/jobs\/(\d+)(\/apply)?$/.exec(hash)
  const jobId = match?.[1]
  const applying = !!match?.[2]
  const onProfile = hash === '#/profile'
  const onSettings = hash === '#/settings'
  const onNotes = hash === '#/notifications'

  // the Chrome extension popup has its own screens, with no top bar or footer
  if (hash === '#/ext' || hash.startsWith('#/ext/')) return <ExtensionApp hash={hash} />

  return (
    <div className="app">
      <header className="header">
        <div className="header__inner">
          <a className="header__brand" href="#/">
            <span className="header__logo" aria-hidden>J</span>
            JobTracker
          </a>
          <AgentChat />
          <NotificationBell on={onNotes} />
          <AccountMenu route={onProfile ? 'profile' : onSettings ? 'settings' : null} />
        </div>
      </header>
      <main className="main">
        {onProfile ? <ProfilePage />
          : onSettings ? <SettingsPage />
          : onNotes ? <NotificationsPage />
          : jobId && applying ? <ApplyPage key={`apply-${jobId}`} id={Number(jobId)} />
          : jobId ? <JobPage key={jobId} id={Number(jobId)} />
          : <Applications />}
      </main>
      <Footer />
    </div>
  )
}
