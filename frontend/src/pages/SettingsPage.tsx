import ApplyNav, { type Section } from '../components/apply/ApplyNav'
import { PlugIcon, PuzzleIcon, RefreshIcon } from '../components/Icons'
import { useSectionNav } from '../components/useSectionNav'
import ConnectorSection from './profile/ConnectorSection'
import SyncSection from './settings/SyncSection'
import './profile/profile.css'
import './ProfilePage.css'
import './SettingsPage.css'

/** Settings, with the same side navigation as the profile: the browser extension, the connected accounts, then syncing them. */
const SECTIONS: Section[] = [
  { id: 'extension', label: 'Extension', icon: <PuzzleIcon /> },
  { id: 'connectors', label: 'Connectors', icon: <PlugIcon /> },
  { id: 'sync', label: 'Sync', icon: <RefreshIcon /> },
]

export default function SettingsPage() {
  const { active, goTo } = useSectionNav(SECTIONS.map((s) => s.id), 's-')

  return (
    <div className="settings">
      <a className="back" href="#/">&larr; All jobs</a>
      <h1>Settings</h1>

      <div className="pwin">
        <ApplyNav sections={SECTIONS} active={active} onSelect={goTo} />
        <div className="pwin__page">
          <section id="s-extension" className="card psec">
            <header className="psec__head">
              <h2>Extension</h2>
              <p className="muted">Your pending jobs and the apply screen in a popup, right in your browser.</p>
            </header>
            <div className="tpl__row">
              <p className="muted">The Chrome extension is coming soon. You can already see how its popup will look.</p>
              <span className="settings__actions">
                <a className="btn" href="#/ext">Preview popup</a>
                <button className="btn" disabled title="Coming soon">Install</button>
              </span>
            </div>
          </section>

          <section id="s-connectors" className="card psec">
            <header className="psec__head">
              <h2>Connectors</h2>
              <p className="muted">Accounts the app can connect to.</p>
            </header>
            <ConnectorSection />
          </section>
          <section id="s-sync" className="card psec">
            <header className="psec__head">
              <h2>Sync</h2>
              <p className="muted">Keep your jobs&rsquo; statuses in step with your Gmail.</p>
            </header>
            <SyncSection />
          </section>
          {/* room at the end, so the last sections can be scrolled up to the top as well */}
          <div className="pwin__end" aria-hidden />
        </div>
      </div>
    </div>
  )
}
