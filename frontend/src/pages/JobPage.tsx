import { useCallback, useEffect, useState } from 'react'
import { api, longDateTime, shortDate, statusLabel, type Contact, type Job, type Status } from '../api'
import ConfirmDelete from '../components/ConfirmDelete'
import { gmailLink } from '../gmail'
import ContactsModal from '../components/ContactsModal'
import { ApplyIcon, MailIcon, TrashIcon, UserIcon, UserPlusIcon } from '../components/Icons'
import StatusMenu from '../components/StatusMenu'
import { formatLocation } from '../location'
import './JobPage.css'

const TYPE: Record<string, string> = { FullTime: 'Full time', PartTime: 'Part time', Contract: 'Contract', Internship: 'Internship' }

/** One job on its own page: its details and description, plus what you can do about its status. */
/** Where the application was made and how it got here: "LinkedIn · synced from Gmail", "Naukri · added manually". */
function sourceOf(job: Job): string {
  const how = job.addedFrom === 'GMAIL' ? 'synced from Gmail' : 'added manually'
  return job.platform ? `${job.platform} · ${how}` : how[0].toUpperCase() + how.slice(1)
}

export default function JobPage({ id }: { id: number }) {
  const [job, setJob] = useState<Job | null>(null)
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [deleting, setDeleting] = useState(false)
  const [contacts, setContacts] = useState<Contact[]>([])
  const [contactsOpen, setContactsOpen] = useState(false)

  const loadContacts = useCallback(() => {
    api.contacts(id).then(setContacts).catch(() => setContacts([]))
  }, [id])

  useEffect(() => {
    loadContacts()
    api.job(id).then(setJob).catch((e) => setError(e instanceof Error ? e.message : String(e)))
    api.description(id).then(setDescription).catch(() => setDescription(''))
  }, [id, loadContacts])

  const changeStatus = async (status: Status) => {
    if (!job) return
    const before = job
    setJob({ ...job, status }) // show it at once
    try {
      setJob(await api.updateStatus(job.id, status))
    } catch (e) {
      setJob(before)
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  const remove = async () => {
    if (!job) return
    try {
      await api.deleteJob(job.id)
      window.location.hash = '#/'
    } catch (e) {
      setDeleting(false)
      setError(e instanceof Error ? e.message : String(e))
    }
  }

  if (error && !job) {
    return (
      <div className="jobpage">
        <a className="back" href="#/">&larr; All jobs</a>
        <div className="alert">{error.includes('404') || error.includes('not found') ? 'That job was not found. It may have been deleted.' : error}</div>
      </div>
    )
  }
  if (!job) return <div className="jobpage"><a className="back" href="#/">&larr; All jobs</a></div>

  const facts: [string, string | null][] = [
    ['Location', formatLocation(job.location)],
    ['Work mode', job.workMode],
    ['Job type', job.jobType ? TYPE[job.jobType] ?? job.jobType : null],
    ['Experience', job.experience],
    ['Salary', job.salary],
    ['Source', sourceOf(job)],
    ['Posting ID', job.postingId],
    ['Added', job.addedAt ? longDateTime(job.addedAt) : shortDate(job.addedOn)],
  ]

  return (
    <div className="jobpage">
      <a className="back" href="#/">&larr; All jobs</a>
      {error && <div className="alert">{error}</div>}

      <header className="jobpage__head">
        <div>
          <h1>{job.company}</h1>
          <p className="jobpage__role">{job.role}</p>
          {job.addedFrom === 'GMAIL' && (
            <p className="jobpage__synced">
              <MailIcon /> This job was synced from Gmail.
              {job.sourceMailId && (
                <a className="btn jobpage__viewmail" href={gmailLink(job.sourceMailId, job.sourceMailAccount)} target="_blank" rel="noreferrer">View mail</a>
              )}
            </p>
          )}
        </div>
        <div className="statusbar">
          {job.status === 'TO_APPLY' ? (
            <>
              <button className="btn btn--outline" onClick={() => { window.location.hash = `#/jobs/${job.id}/apply` }}>
                <ApplyIcon /> Apply
              </button>
              <button className="iconbtn iconbtn--delete iconbtn--boxed" title="Delete job" aria-label="Delete job" onClick={() => setDeleting(true)}>
                <TrashIcon />
              </button>
            </>
          ) : (
            // left to right: the status itself, the contacts, then the update button
            <>
              <span className={`status status--${job.status.toLowerCase()}`}>{statusLabel(job.status)}</span>
              {contacts.length === 0 ? (
                <button className="btn btn--icon" title="Add contact" aria-label="Add contact" onClick={() => setContactsOpen(true)}>
                  <UserPlusIcon />
                </button>
              ) : (
                <button className="btn" title="Contacts" aria-label={`Contacts (${contacts.length})`} onClick={() => setContactsOpen(true)}>
                  <UserIcon /> <span className="count">{contacts.length}</span>
                </button>
              )}
              <StatusMenu current={job.status} onPick={changeStatus} />
            </>
          )}
        </div>
      </header>

      <section className="card jobpage__card">
        <dl className="facts">
          {facts.map(([k, v]) => (
            <div key={k}>
              <dt>{k}</dt>
              <dd>{v ?? '—'}</dd>
            </div>
          ))}
        </dl>
        {job.skills.length > 0 && (
          <div className="skills">
            {job.skills.map((s) => <span key={s}>{s}</span>)}
          </div>
        )}
        {job.link && (
          <a className="openlink" href={job.link} target="_blank" rel="noreferrer">Open the original posting ↗</a>
        )}
      </section>

      <section className="card jobpage__card">
        <h2>Job description</h2>
        {description ? <div className="desc">{description}</div> : <p className="muted">No description was saved for this job.</p>}
      </section>

      {contactsOpen && (
        <ContactsModal jobId={job.id} contacts={contacts} onChanged={loadContacts} onClose={() => setContactsOpen(false)} />
      )}
      {deleting && <ConfirmDelete job={job} onCancel={() => setDeleting(false)} onConfirm={remove} />}
    </div>
  )
}
