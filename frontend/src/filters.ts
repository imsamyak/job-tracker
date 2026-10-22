import type { Job, Status } from './api'
import { formatLocation } from './location'

/** Which jobs to show. An empty list for a field means "no restriction on that field". */
export type Since = 'any' | '7' | '30'

export interface Filters {
  statuses: Status[]
  locations: string[]
  since: Since
}

export const NO_FILTERS: Filters = { statuses: [], locations: [], since: 'any' }

/** How many separate restrictions are switched on (shown as the badge on the Filter button). */
export const activeCount = (f: Filters) => f.statuses.length + f.locations.length + (f.since === 'any' ? 0 : 1)

/** When a job was added, as a number. Older jobs only have a date. */
export const addedTime = (j: Job) => (j.addedAt ? Date.parse(j.addedAt) : Date.parse(`${j.addedOn}T00:00:00`))

export function applyFilters(jobs: Job[], f: Filters): Job[] {
  const cutoff = f.since === 'any' ? 0 : Date.now() - Number(f.since) * 86_400_000
  return jobs.filter(
    (j) =>
      (f.statuses.length === 0 || f.statuses.includes(j.status)) &&
      (f.locations.length === 0 || f.locations.includes(formatLocation(j.location) ?? '')) &&
      addedTime(j) >= cutoff,
  )
}

/** Every distinct place ("Pune, MH") in the list, for the Location filter. */
export const locationsOf = (jobs: Job[]): string[] =>
  [...new Set(jobs.map((j) => formatLocation(j.location)).filter((l): l is string => !!l))].sort((a, b) => a.localeCompare(b))

export type SortKey = 'added' | 'company'
export interface Sort {
  key: SortKey
  dir: 'asc' | 'desc'
}

/** Newest first is the natural start for dates, A to Z for names. */
export const defaultDir = (key: SortKey): Sort['dir'] => (key === 'added' ? 'desc' : 'asc')

export function sortJobs(jobs: Job[], s: Sort): Job[] {
  const sign = s.dir === 'asc' ? 1 : -1
  return [...jobs].sort((a, b) => {
    if (s.key === 'company') {
      const c = a.company.localeCompare(b.company, undefined, { sensitivity: 'base' })
      if (c !== 0) return c * sign
      return addedTime(b) - addedTime(a) // same company: newest first
    }
    return (addedTime(a) - addedTime(b)) * sign || b.id - a.id
  })
}

/**
 * Free-text search: every word typed has to appear somewhere in the company, role, location, platform, skills
 * or the posting's own job id (a leading "#" is ignored, so "#4521" finds job id 4521).
 */
export function matchesQuery(j: Job, query: string): boolean {
  const words = query.toLowerCase().split(/\s+/).map((w) => w.replace(/^#/, '')).filter(Boolean)
  if (words.length === 0) return true
  const hay = [j.company, j.role, j.location, formatLocation(j.location), j.platform, j.postingId, ...j.skills]
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
  return words.every((w) => hay.includes(w))
}
