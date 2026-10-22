import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, type Intake, type Job } from '../../api'
import ConfirmDelete from '../../components/ConfirmDelete'
import { ApplyIcon, CloseIcon, GlobeIcon, SearchIcon, TrashIcon } from '../../components/Icons'
import Waiting from '../../components/Waiting'
import { matchesQuery } from '../../filters'
import { formatLocation } from '../../location'

/** Home: only the jobs still to apply to, newest first. Search and add are icons in the top bar. */
export default function ExtHome() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [waiting, setWaiting] = useState<Intake[]>([])
  const [loaded, setLoaded] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [note, setNote] = useState('') // what the last paste or page read did
  const [reading, setReading] = useState(false)
  const [deleting, setDeleting] = useState<Job | null>(null)
  const [searching, setSearching] = useState(false)
  const [query, setQuery] = useState('')
  const searchBox = useRef<HTMLInputElement>(null)

  const refresh = useCallback(async () => {
    try {
      const [j, w] = await Promise.all([api.jobs(), api.intakes()])
      setJobs(j)
      setWaiting(w)
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoaded(true)
    }
  }, [])

  useEffect(() => {
    void refresh()
    const timer = setInterval(() => void refresh(), 4000)
    return () => clearInterval(timer)
  }, [refresh])

  useEffect(() => {
    if (searching) searchBox.current?.focus()
  }, [searching])

  const pending = useMemo(
    () => jobs
      .filter((j) => j.status === 'TO_APPLY')
      .sort((a, b) => Date.parse(b.addedAt ?? b.addedOn) - Date.parse(a.addedAt ?? a.addedOn)),
    [jobs],
  )
  const visible = useMemo(() => pending.filter((j) => matchesQuery(j, query)), [pending, query])

  const confirmDelete = async () => {
    if (!deleting) return
    try {
      await api.deleteJob(deleting.id)
      setJobs((all) => all.filter((j) => j.id !== deleting.id))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
    setDeleting(null)
  }

  const say = (text: string) => {
    setNote(text)
    setTimeout(() => setNote((n) => (n === text ? '' : n)), 3500)
  }

  const queue = async (what: () => Promise<unknown>) => {
    try {
      await what()
      say('Added. It is being read.')
      void refresh()
    } catch (e) {
      say(e instanceof Error ? e.message : 'Could not add that.')
    }
  }

  // anything pasted anywhere on the screen is added at once: a screenshot, or text (a job description or a link)
  const onPaste = (e: React.ClipboardEvent) => {
    const image = Array.from(e.clipboardData.files).find((f) => f.type.startsWith('image/'))
    if (image) {
      e.preventDefault()
      void queue(() => api.addImage(image))
      return
    }
    const text = e.clipboardData.getData('text').trim()
    if (text.length > 0) {
      e.preventDefault()
      void queue(() => api.addText(text))
    }
  }

  // "read this page": inside the extension, its popup reads the open tab and answers; elsewhere there is nothing to read
  const readPage = () => {
    if (window.parent === window) return say('Reading a page only works inside the browser extension.')
    setReading(true)
    window.parent.postMessage({ type: 'jt-read-page' }, '*')
    setTimeout(() => setReading(false), 8000)
  }

  useEffect(() => {
    const onMessage = (e: MessageEvent) => {
      if (e.source !== window.parent || !e.data || e.data.type !== 'jt-page') return
      setReading(false)
      if (e.data.error || !e.data.text) return say(e.data.error || 'Nothing to read on this page.')
      void queue(() => api.addText(`${e.data.url}\n\n${e.data.title}\n\n${e.data.text}`))
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  })

  const closeSearch = () => {
    setSearching(false)
    setQuery('')
  }

  return (
    <div className="exth">
      <header className="extbar">
        {searching ? (
          <>
            <SearchIcon />
            <input
              ref={searchBox}
              className="extbar__search"
              value={query}
              placeholder="Search company, role or job id"
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Escape') closeSearch() }}
            />
            <button className="exticon" title="Close search" aria-label="Close search" onClick={closeSearch}><CloseIcon /></button>
          </>
        ) : (
          <>
            <span className="extbar__brand"><span className="header__logo" aria-hidden>J</span>JobTracker</span>
            <span className="extbar__spacer" />
            <button className="exticon" title="Search" aria-label="Search" onClick={() => setSearching(true)}><SearchIcon /></button>
            <button className="exticon exticon--accent" title="Read the page I have open" aria-label="Read this page" disabled={reading} onClick={readPage}><GlobeIcon /></button>
          </>
        )}
      </header>

      <div className="extbody" onPaste={onPaste}>
        <input
          className="extpaste"
          placeholder="Paste a job link, text or screenshot"
          aria-label="Paste a job"
          onKeyDown={(e) => {
            const v = e.currentTarget.value.trim()
            if (e.key === 'Enter' && v) {
              e.currentTarget.value = ''
              void queue(() => api.addText(v))
            }
          }}
        />
        {note && <p className="extnote">{note}</p>}
        <Waiting items={waiting} onChange={refresh} />
        {error && <div className="alert">{error}</div>}

        {loaded && pending.length > 0 && (
          <p className="extcount">{query.trim() ? `${visible.length} of ${pending.length} pending` : `${pending.length} to apply`}</p>
        )}

        <ul className="extlist">
          {visible.map((j) => {
            const where = formatLocation(j.location)
            return (
              <li key={j.id} className="extitem">
                <a className="extjob" href={`#/ext/jobs/${j.id}`}>
                  <span className="extjob__main">
                    <span className="extjob__company">{j.company}</span>
                    <span className="extjob__role">{j.role}{where ? ` · ${where}` : ''}</span>
                    {j.postingId && <span className="extjob__id">#{j.postingId}</span>}
                  </span>
                </a>
                <button className="exticon exticon--danger" title="Delete" aria-label={`Delete ${j.company}`} onClick={() => setDeleting(j)}>
                  <TrashIcon />
                </button>
                <a className="exticon exticon--accent" href={`#/ext/jobs/${j.id}`} title="Apply" aria-label={`Apply to ${j.company}`}>
                  <ApplyIcon />
                </a>
              </li>
            )
          })}
        </ul>

        {loaded && pending.length === 0 && (
          <div className="extempty">
            <strong>Nothing to apply to</strong>
            Paste a job above, or open a job page and press the page button.
          </div>
        )}
        {loaded && pending.length > 0 && visible.length === 0 && (
          <div className="extempty"><strong>No pending job matches</strong></div>
        )}
      </div>

      {deleting && <ConfirmDelete job={deleting} onCancel={() => setDeleting(null)} onConfirm={confirmDelete} />}
    </div>
  )
}
