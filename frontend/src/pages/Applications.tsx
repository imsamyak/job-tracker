import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, shortDate, statusLabel, type Intake, type Job, type Status } from '../api'
import AddBox from '../components/AddBox'
import ConfirmDelete from '../components/ConfirmDelete'
import FilterMenu from '../components/FilterMenu'
import { ApplyIcon, SearchIcon, SortIcon, TrashIcon } from '../components/Icons'
import { formatLocation } from '../location'
import StatusMenu from '../components/StatusMenu'
import Waiting from '../components/Waiting'
import {
  activeCount, applyFilters, defaultDir, locationsOf, matchesQuery, NO_FILTERS, sortJobs,
  type Filters, type Sort, type SortKey,
} from '../filters'
import './Applications.css'

const FAST_MS = 2000 // while something is being read
const SLOW_MS = 10000

export default function Applications() {
  const [jobs, setJobs] = useState<Job[]>([])
  const [waiting, setWaiting] = useState<Intake[]>([])
  const [loaded, setLoaded] = useState(false)
  const [adding, setAdding] = useState(false)
  const [deleting, setDeleting] = useState<Job | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [filters, setFilters] = useState<Filters>(NO_FILTERS)
  const [query, setQuery] = useState('')
  const [sort, setSort] = useState<Sort>({ key: 'added', dir: 'desc' })
  const busy = useRef(false)

  const refresh = useCallback(async () => {
    try {
      const [j, w] = await Promise.all([api.jobs(), api.intakes()])
      setJobs(j)
      setWaiting(w)
      busy.current = w.some((i) => i.status === 'PENDING' || i.status === 'PROCESSING')
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoaded(true)
    }
  }, [])

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>
    const loop = async () => {
      await refresh()
      timer = setTimeout(loop, busy.current ? FAST_MS : SLOW_MS)
    }
    loop()
    return () => clearTimeout(timer)
  }, [refresh])

  const confirmDelete = async () => {
    if (!deleting) return
    try {
      await api.deleteJob(deleting.id)
      setJobs((all) => all.filter((j) => j.id !== deleting.id))
      setDeleting(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setDeleting(null)
    }
  }

  const changeStatus = async (job: Job, status: Status) => {
    setJobs((all) => all.map((j) => (j.id === job.id ? { ...j, status } : j))) // show it at once
    try {
      await api.updateStatus(job.id, status)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      refresh()
    }
  }

  // One sort at a time. Clicking the active column reverses it; clicking the other one switches to it.
  const clickSort = (key: SortKey) =>
    setSort((s) => (s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: defaultDir(key) }))

  const locations = useMemo(() => locationsOf(jobs), [jobs])
  const visible = useMemo(
    () => sortJobs(applyFilters(jobs, filters).filter((j) => matchesQuery(j, query)), sort),
    [jobs, filters, query, sort],
  )
  const searching = query.trim() !== ''
  const filtering = activeCount(filters) > 0 || searching
  const pending = jobs.filter((j) => j.status === 'TO_APPLY').length
  const open = (id: number) => { window.location.hash = `#/jobs/${id}` }

  const sortHead = (key: SortKey, label: string) => {
    const active = sort.key === key
    return (
      <th aria-sort={active ? (sort.dir === 'asc' ? 'ascending' : 'descending') : 'none'}>
        <button className={active ? 'sorthead sorthead--active' : 'sorthead'} onClick={() => clickSort(key)}
                title={`Sort by ${label.toLowerCase()}`}>
          {label} <SortIcon dir={active ? sort.dir : null} />
        </button>
      </th>
    )
  }

  return (
    <div className="apps">
      <div className="toolbar">
        <h1 className="sr-only">Jobs</h1>
        <div className="toolbar__actions">
          <label className="search">
            <SearchIcon />
            <input
              type="search"
              value={query}
              placeholder="Search"
              aria-label="Search jobs by company, role, location or job id"
              onChange={(e) => setQuery(e.target.value)}
            />
          </label>
          <FilterMenu value={filters} onChange={setFilters} locations={locations} />
          <button className="btn btn--outline" title="Add job" aria-label="Add job" onClick={() => setAdding(true)}>+ Add</button>
        </div>
      </div>

      <Waiting items={waiting} onChange={refresh} />

      {error && <div className="alert">{error}</div>}

      <section className="card list">
        {loaded && jobs.length > 0 && (
          <p className="list__foot">
            {filtering
              ? `${visible.length} of ${jobs.length} jobs`
              : `${jobs.length} ${jobs.length === 1 ? 'job' : 'jobs'} · ${pending} pending`}
          </p>
        )}
        <div className="list__scroll">
          <table>
            <thead>
              <tr>
                {sortHead('company', 'Company')}
                <th>Role</th>
                <th>Location</th>
                <th>Status</th>
                {sortHead('added', 'Added')}
                <th className="actions-head">Actions</th>
              </tr>
            </thead>
            <tbody>
              {visible.map((j) => (
                <tr key={j.id} className="row" onClick={() => open(j.id)}>
                  <td className="strong"><a href={`#/jobs/${j.id}`} onClick={(e) => e.stopPropagation()}>{j.company}</a>{j.postingId && <span className="jobid" title="Job ID">#{j.postingId}</span>}</td>
                  <td className="role">{j.role}</td>
                  <td>{formatLocation(j.location) ?? '—'}</td>
                  <td><span className={`status status--${j.status.toLowerCase()}`}>{statusLabel(j.status)}</span></td>
                  <td className="muted">{shortDate(j.addedOn)}</td>
                  <td className="actions-cell">
                    {j.status !== 'TO_APPLY' && (
                      <div className="actions">
                        <StatusMenu compact current={j.status} onPick={(s) => changeStatus(j, s)} />
                      </div>
                    )}
                    {j.status === 'TO_APPLY' && (
                      <div className="actions">
                        <button
                          className="iconbtn iconbtn--apply"
                          title="Apply"
                          aria-label={`Apply to ${j.company}`}
                          onClick={(e) => { e.stopPropagation(); window.location.hash = `#/jobs/${j.id}/apply` }}
                        >
                          <ApplyIcon />
                        </button>
                        <button
                          className="iconbtn iconbtn--delete"
                          title="Delete"
                          aria-label={`Delete ${j.company}`}
                          onClick={(e) => { e.stopPropagation(); setDeleting(j) }}
                        >
                          <TrashIcon />
                        </button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
              {loaded && jobs.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty">
                    <div className="empty__title">No jobs yet</div>
                    Press <strong>Add job</strong> and paste a job description, a link or a screenshot.
                  </td>
                </tr>
              )}
              {loaded && jobs.length > 0 && visible.length === 0 && (
                <tr>
                  <td colSpan={6} className="empty">
                    <div className="empty__title">{searching ? 'No jobs match your search' : 'No jobs match these filters'}</div>
                    <button className="link" onClick={() => { setFilters(NO_FILTERS); setQuery('') }}>
                      {searching ? 'Clear search and filters' : 'Clear filters'}
                    </button>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </section>

      {adding && <AddBox onClose={() => setAdding(false)} onAdded={refresh} />}
      {deleting && <ConfirmDelete job={deleting} onCancel={() => setDeleting(null)} onConfirm={confirmDelete} />}
    </div>
  )
}
