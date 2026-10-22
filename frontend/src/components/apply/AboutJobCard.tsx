import { useEffect, useState } from 'react'
import { api, type Job } from '../../api'
import { formatLocation } from '../../location'

/** The job at a glance, so you do not have to go back while filling in a form. */
export default function AboutJobCard({ job }: { job: Job }) {
  const [open, setOpen] = useState(false)
  const [description, setDescription] = useState<string | null>(null)

  useEffect(() => {
    if (open && description === null) api.description(job.id).then(setDescription).catch(() => setDescription(''))
  }, [open, description, job.id])

  const facts: [string, string | null][] = [
    ['Location', formatLocation(job.location)],
    ['Experience', job.experience],
    ['Salary', job.salary],
    ['Work mode', job.workMode],
  ]

  return (
    <section className="card acard">
      <header className="acard__head">
        <h2>About this job</h2>
      </header>
      <dl className="mini">
        {facts.filter(([, v]) => v).map(([k, v]) => (
          <div key={k}><dt>{k}</dt><dd>{v}</dd></div>
        ))}
      </dl>
      {job.skills.length > 0 && <div className="chips">{job.skills.map((s) => <span key={s}>{s}</span>)}</div>}
      <button className="link" onClick={() => setOpen(!open)}>{open ? 'Hide job description' : 'Show job description'}</button>
      {open && <div className="jd">{description ?? 'Loading...'}{description === '' && 'No description was saved for this job.'}</div>}
    </section>
  )
}
