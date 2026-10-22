import { useEffect, useState } from 'react'
import { api } from '../../api'
import { loadProfile, type SummaryVariant } from '../../profileStore'
import { useCopy } from './useCopy'
import { EyeIcon, FileIcon, FolderIcon, SparklesIcon } from '../Icons'

type Tailored = Awaited<ReturnType<typeof api.tailored>>
type Bullets = Awaited<ReturnType<typeof api.tailoredBullets>>

/**
 * The documents for this job. The resume prepares itself: as soon as the job is opened, its skills are chosen for the role
 * (free, nothing to press). The cover letter still needs Claude and is switched off for now. A document that has been made as
 * a file shows two icons: open the folder it is in, and view it in the browser.
 */
export default function DocumentsCard({ jobId, generated = {} }: { jobId: number; generated?: Record<string, boolean> }) {
  const [tailored, setTailored] = useState<Tailored | null>(null)
  const [failed, setFailed] = useState(false)
  const [bullets, setBullets] = useState<Bullets | null>(null)
  const [ai, setAi] = useState({ busy: false, text: '', error: '' })
  const { copied, copy } = useCopy()
  const profile = loadProfile()

  useEffect(() => {
    api.tailored(jobId).then(setTailored).catch(() => setFailed(true))
    // your own bullets, one per line of each job's details: chosen and ordered, never rewritten
    const entries = loadProfile().experience.map((e) => ({ id: e.id, bullets: e.details.split('\n').map((l) => l.trim()).filter(Boolean) }))
    if (entries.some((e) => e.bullets.length)) api.tailoredBullets(jobId, entries).then(setBullets).catch(() => setBullets(null))
  }, [jobId])

  const variant = tailored?.summary as SummaryVariant | null | undefined
  const summary = (variant && profile.summaries[variant]?.trim()) || profile.personal.about.trim()

  // only on press: the one AI call here
  const tailorSummary = async () => {
    setAi({ busy: true, text: '', error: '' })
    try {
      const job = await api.job(jobId)
      const skills = tailored?.sections.flatMap((s) => s.skills).slice(0, 12).join(', ') ?? ''
      const r = await api.rewrite('ABOUT', summary, `${job.role} at ${job.company}`,
        `Tailor this summary for the ${job.role} role${skills ? `, putting forward skills it already mentions among: ${skills}` : ''}. Do not add anything that is not in the text.`)
      setAi({ busy: false, text: r.text, error: '' })
    } catch (e) {
      setAi({ busy: false, text: '', error: e instanceof Error ? e.message : 'Could not tailor the summary.' })
    }
  }

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
          <div className="doc__main">
            {summary && (
              <div className="tailored">
                <strong>Summary{variant ? ` (${variant})` : ''}</strong>
                <span>{ai.text || summary}</span>
                <span>
                  <button type="button" className="btn" disabled={ai.busy} title="Rewrite this summary for the job with AI" onClick={() => void tailorSummary()}>
                    {ai.busy ? <><span className="spinner" aria-hidden /> Tailoring</> : <><SparklesIcon /> Tailor with AI</>}
                  </button>{' '}
                  <button type="button" className="btn" onClick={() => void copy('summary', ai.text || summary)}>{copied === 'summary' ? 'Copied' : 'Copy'}</button>
                </span>
                {ai.error && <span className="conn__error">{ai.error}</span>}
              </div>
            )}
            {bullets && bullets.some((e) => e.bullets.length > 0) && (
              <div className="tailored">
                <strong>Experience bullets, best first</strong>
                {bullets.map((e) => {
                  const exp = profile.experience.find((x) => x.id === e.id)
                  return e.bullets.length === 0 ? null : (
                    <div key={e.id}>
                      <em>{exp ? [exp.role, exp.company].filter(Boolean).join(' · ') : ''}</em>
                      <ul>{e.bullets.map((b) => <li key={b.text}>{b.text}</li>)}</ul>
                    </div>
                  )
                })}
              </div>
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
