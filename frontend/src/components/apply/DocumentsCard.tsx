import { useEffect, useState } from 'react'
import { api } from '../../api'
import { EyeIcon, FileIcon, FolderIcon, SparklesIcon } from '../Icons'

type Tailored = Awaited<ReturnType<typeof api.tailored>>

/**
 * The documents for this job. The resume prepares itself: as soon as the job is opened, its skills are chosen for the role
 * (free, nothing to press). The cover letter still needs Claude and is switched off for now. A document that has been made as
 * a file shows two icons: open the folder it is in, and view it in the browser.
 */
export default function DocumentsCard({ jobId, generated = {} }: { jobId: number; generated?: Record<string, boolean> }) {
  const [tailored, setTailored] = useState<Tailored | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    api.tailored(jobId).then(setTailored).catch(() => setFailed(true))
  }, [jobId])

  return (
    <section className="card acard">
      <header className="acard__head">
        <h2>Documents</h2>
      </header>
      <ul className="docs">
        <li className="doc doc--tall">
          <span className="doc__icon"><FileIcon /></span>
          <div className="doc__main">
            <div className="doc__name">Resume</div>
            <div className="doc__hint">Skills chosen for this job</div>
            {failed && <div className="doc__hint">Could not prepare the skills.</div>}
            {tailored && tailored.sections.length === 0 && (
              <div className="doc__hint">The description does not say what kind of role this is, so no skills are chosen yet.</div>
            )}
            {tailored && tailored.sections.length > 0 && (
              <ul className="tailored">
                {tailored.sections.map((s) => (
                  <li key={s.label}><strong>{s.label}:</strong> {s.skills.join(', ')}</li>
                ))}
              </ul>
            )}
          </div>
          {generated.resume && (
            <span className="doc__done">
              <button className="iconbtn iconbtn--apply" title="Open folder" aria-label="Open the folder of the resume"><FolderIcon /></button>
              <button className="iconbtn iconbtn--apply" title="View" aria-label="View the resume"><EyeIcon /></button>
            </span>
          )}
        </li>
        <li className="doc">
          <span className="doc__icon"><FileIcon /></span>
          <div className="doc__main">
            <div className="doc__name">Cover letter</div>
            <div className="doc__hint">Written for this role</div>
          </div>
          <button className="btn" disabled title="Coming soon">
            <SparklesIcon /> Generate
          </button>
        </li>
      </ul>
    </section>
  )
}
