import { useEffect, useState } from 'react'
import { api, type Job } from '../../api'
import AboutJobCard from '../../components/apply/AboutJobCard'
import AskCard from '../../components/apply/AskCard'
import ContactCard from '../../components/apply/ContactCard'
import DocumentsCard from '../../components/apply/DocumentsCard'
import QuickLinksCard from '../../components/apply/QuickLinksCard'
import ReferralCard from '../../components/apply/ReferralCard'
import { ArrowLeftIcon, CheckIcon, ExternalIcon } from '../../components/Icons'
import { formatLocation } from '../../location'
import { SECTIONS } from '../ApplyPage'
import '../../components/apply/apply.css'

/** Apply screen: the same sections as the web app, with the section tabs along the bottom. */
export default function ExtApply({ id }: { id: number }) {
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [section, setSection] = useState('about')

  useEffect(() => {
    api.job(id).then(setJob).catch((e) => setError(e instanceof Error ? e.message : String(e)))
  }, [id])

  const markApplied = async () => {
    setBusy(true)
    try {
      await api.updateStatus(id, 'APPLIED')
      window.location.hash = '#/ext'
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
    }
  }

  const where = job ? formatLocation(job.location) : null

  return (
    <div className="exta">
      <header className="extbar">
        <a className="exticon" href="#/ext" title="Back" aria-label="Back to jobs"><ArrowLeftIcon /></a>
        <span className="extbar__title">
          <strong>{job?.company ?? 'Loading...'}</strong>
          {job && <span>{job.role}{where ? ` · ${where}` : ''}</span>}
        </span>
        {job?.link && (
          <a className="exticon" href={job.link} target="_blank" rel="noreferrer" title="Open posting" aria-label="Open posting"><ExternalIcon /></a>
        )}
        {job && job.status === 'TO_APPLY' && (
          <button className="exticon exticon--accent" title="Mark as applied" aria-label="Mark as applied" disabled={busy} onClick={markApplied}><CheckIcon /></button>
        )}
      </header>

      <div className="extbody">
        {error && <div className="alert">{error}</div>}
        {job && (
          <>
            {section === 'about' && <AboutJobCard job={job} />}
            {section === 'documents' && <DocumentsCard jobId={id} />}
            {section === 'links' && <QuickLinksCard />}
            {section === 'ask' && <AskCard />}
            {section === 'referral' && <ReferralCard job={job} />}
            {section === 'contact' && <ContactCard jobId={id} />}
          </>
        )}
      </div>

      <nav className="exttabs" aria-label="Apply sections">
        {SECTIONS.map((s) => (
          <button
            key={s.id}
            className={section === s.id ? 'exttab exttab--on' : 'exttab'}
            aria-current={section === s.id ? 'page' : undefined}
            onClick={() => setSection(s.id)}
          >
            {s.icon}
            <span>{s.label}</span>
          </button>
        ))}
      </nav>
    </div>
  )
}
