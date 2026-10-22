import { useEffect, useState } from 'react'
import { api, type Job } from '../api'
import AboutJobCard from '../components/apply/AboutJobCard'
import ApplyNav, { type Section } from '../components/apply/ApplyNav'
import AskCard from '../components/apply/AskCard'
import ContactCard from '../components/apply/ContactCard'
import DocumentsCard from '../components/apply/DocumentsCard'
import QuickLinksCard from '../components/apply/QuickLinksCard'
import ReferralCard from '../components/apply/ReferralCard'
import { CheckIcon, ExternalIcon, FileIcon, InfoIcon, LinkIcon, MailIcon, SparklesIcon, UserIcon } from '../components/Icons'
import { formatLocation } from '../location'
import '../components/apply/apply.css'
import './ApplyPage.css'

/** The sections of the apply window, in the order they appear in its navigation bar. */
export const SECTIONS: Section[] = [
  { id: 'about', label: 'About', icon: <InfoIcon /> },
  { id: 'documents', label: 'Documents', icon: <FileIcon /> },
  { id: 'links', label: 'Links', icon: <LinkIcon /> },
  { id: 'ask', label: 'Ask', icon: <SparklesIcon /> },
  { id: 'referral', label: 'Referral', icon: <MailIcon /> },
  { id: 'contact', label: 'Contact', icon: <UserIcon /> },
]

/**
 * Everything you need while applying to one job. A navigation bar on the right lists the sections
 * (about the job, documents, links, ask, referral note, contact); the one you pick opens on the left.
 * Opening the posting and marking the job as applied sit in the header.
 *
 * A first design: documents and the "ask" box are shown switched off until they exist.
 */
export default function ApplyPage({ id }: { id: number }) {
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
      window.location.hash = `#/jobs/${id}`
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
    }
  }

  if (!job) {
    return (
      <div className="applypage">
        <a className="back" href={`#/jobs/${id}`}>&larr; Back to job</a>
        {error && <div className="alert">{error}</div>}
      </div>
    )
  }

  const where = formatLocation(job.location)

  return (
    <div className="applypage">
      <a className="back" href={`#/jobs/${id}`}>&larr; Back to job</a>
      {error && <div className="alert">{error}</div>}

      <header className="applypage__head">
        <div>
          <h1>Apply to {job.company}</h1>
          <p className="applypage__role">{job.role}{where ? ` · ${where}` : ''}</p>
        </div>
        <div className="applypage__actions">
          {job.link && (
            <a className="btn" href={job.link} target="_blank" rel="noreferrer"><ExternalIcon /> Open posting</a>
          )}
          {job.status === 'TO_APPLY' ? (
            <button className="btn btn--primary" onClick={markApplied} disabled={busy}>
              <CheckIcon /> {busy ? 'Saving...' : 'Mark as applied'}
            </button>
          ) : (
            <span className="muted">Already applied</span>
          )}
        </div>
      </header>

      <div className="awin">
        <div className="awin__page">
          {section === 'about' && <AboutJobCard job={job} />}
          {section === 'documents' && <DocumentsCard jobId={id} />}
          {section === 'links' && <QuickLinksCard />}
          {section === 'ask' && <AskCard />}
          {section === 'referral' && <ReferralCard job={job} />}
          {section === 'contact' && <ContactCard jobId={id} />}
        </div>
        <ApplyNav sections={SECTIONS} active={section} onSelect={setSection} />
      </div>
    </div>
  )
}
