export type Status = 'TO_APPLY' | 'APPLIED' | 'IN_REVIEW' | 'REJECTED'

export const STATUSES: { id: Status; label: string }[] = [
  { id: 'TO_APPLY', label: 'Pending' },
  { id: 'APPLIED', label: 'Applied' },
  { id: 'IN_REVIEW', label: 'In Talks' },
  { id: 'REJECTED', label: 'Rejected' },
]

export interface Job {
  id: number
  company: string
  role: string
  location: string | null
  workMode: string | null
  jobType: string | null
  experience: string | null
  salary: string | null
  postingId: string | null
  /** For a job synced from Gmail: which email it came from, and in which account. */
  sourceMailId: string | null
  sourceMailAccount: string | null
  platform: string | null
  link: string | null
  addedFrom: string | null
  status: Status
  addedOn: string
  addedAt: string | null
  skills: string[]
}

export const statusLabel = (s: Status) => STATUSES.find((x) => x.id === s)?.label ?? s

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/** "2026-10-22" -> "22 Oct" (read from the text, so no time-zone shift can change the day). */
export function shortDate(iso: string): string {
  const [, m, d] = iso.split('-')
  const month = MONTHS[Number(m) - 1]
  return month ? `${Number(d)} ${month}` : iso
}

/** An ISO moment -> "17:51, 22 Oct": 24-hour time first, then the date with a short month and no year, in the viewer's time zone. */
export function longDateTime(iso: string): string {
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${hh}:${mm}, ${d.getDate()} ${MONTHS[d.getMonth()]}`
}

export interface Contact {
  id: number
  jobId: number
  name: string
  role: string | null
  /** An email address or a phone number, as written. */
  contact: string | null
}

/** What the contact form sends. Only the name is required. */
export type ContactInput = Omit<Contact, 'id' | 'jobId'>

export type IntakeStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'DUPLICATE' | 'FAILED'

export interface Intake {
  id: number
  kind: 'TEXT' | 'IMAGE' | 'LINK'
  status: IntakeStatus
  label: string
  message: string | null
  jobId: number | null
  createdAt: string
}

export interface ResumeInfo { present: boolean; filename: string | null; size: number | null; uploadedAt: string | null }

/** What the backend read out of the resume, in the shape of the profile page. */
export interface ExtractedProfile {
  personal: { name: string; headline: string; experience: string; location: string; about: string }
  experience: { company: string; role: string; start: string; end: string; current: boolean; details: string }[]
  projects: { name: string; links: { label: string; value: string }[]; stack: string; details: string }[]
  skills: { name: string; items: string[] }[]
  contact: Record<'emails' | 'phones' | 'links', { label: string; value: string }[]>
}

export interface MailAccount { id: number; email: string; addedAt: string; lastSyncedAt: string | null; lastError: string | null }

export interface MailSyncState {
  running: boolean
  lastRunAt: string | null
  /** What the last run did: emails read, how many mentioned a tracked company, how many Claude judged, how many jobs changed. */
  lastRun: { read: number; matched: number; asked: number; changed: number; added: number; more: boolean } | null
  /** While running: emails downloaded so far, out of how many. */
  progress: { done: number; total: number } | null
  /** The emails that mentioned one of your jobs, and what was decided about each, newest first. */
  checked: { sender: string | null; subject: string | null; outcome: string | null; reason: string | null; company: string | null; receivedAt: string | null }[]
  /** The emails that changed a job's status, newest first. */
  recent: { jobId: number | null; company: string | null; role: string | null; status: Status; subject: string | null; receivedAt: string | null }[]
}

export interface GithubStatus { connected: boolean; login: string | null; connectedAt: string | null }

export interface ClaudeStatus { installed: boolean; loggedIn: boolean; method: 'subscription' | 'api' | null; email: string | null }

export interface Notification {
  id: number
  jobId: number | null
  company: string | null
  role: string | null
  status: Status
  kind: 'ADDED' | 'UPDATED'
  subject: string | null
  sender: string | null
  /** Where the application was made (LinkedIn, Naukri, Workday...). */
  platform: string | null
  reason: string | null
  receivedAt: string | null
  createdAt: string
  mailId: string
  mailAccount: string | null
}

async function call<T>(url: string, init?: RequestInit): Promise<T> {
  const res = await fetch(url, init)
  if (!res.ok) {
    let message = `${res.status} ${res.statusText}`
    try {
      const body = await res.json()
      if (body.message) message = body.message
    } catch {
      /* keep the status text */
    }
    throw new Error(message)
  }
  // 204 and 202 answer with no body at all
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

const json = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
})

export const api = {
  jobs: () => call<Job[]>('/api/jobs'),
  job: (id: number) => call<Job>(`/api/jobs/${id}`),
  description: async (id: number): Promise<string> => {
    const res = await fetch(`/api/jobs/${id}/description`)
    if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
    return res.text()
  },
  updateStatus: (id: number, status: Status) => call<Job>(`/api/jobs/${id}/status`, json('PATCH', { status })),
  deleteJob: (id: number) => call<void>(`/api/jobs/${id}`, { method: 'DELETE' }),

  contacts: (jobId: number) => call<Contact[]>(`/api/jobs/${jobId}/contacts`),
  addContact: (jobId: number, c: ContactInput) => call<Contact>(`/api/jobs/${jobId}/contacts`, json('POST', c)),
  updateContact: (id: number, c: ContactInput) => call<Contact>(`/api/contacts/${id}`, json('PUT', c)),
  deleteContact: (id: number) => call<void>(`/api/contacts/${id}`, { method: 'DELETE' }),

  resume: () => call<ResumeInfo>('/api/resume'),
  uploadResume: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return call<ResumeInfo>('/api/resume', { method: 'POST', body: form })
  },
  projectFromLink: (url: string) => call<{ name: string; link: string; stack: string; details: string }>('/api/projects/from-link', json('POST', { url })),
  mailAccounts: () => call<MailAccount[]>('/api/mail-accounts'),
  addMailAccount: (email: string, appPassword: string) => call<MailAccount>('/api/mail-accounts', json('POST', { email, appPassword })),
  /** A proposed rewrite of a piece of profile text. instruction: the person's own request, optional. */
  rewrite: (kind: 'ABOUT' | 'EXPERIENCE' | 'PROJECT' | 'SKILLS', text: string, context: string, instruction: string) =>
    call<{ text: string }>('/api/rewrite', json('POST', { kind, text, context, instruction })),
  askAi: (messages: { role: 'user' | 'assistant'; text: string }[]) =>
    call<{ reply: string; done: string[]; confirm: { label: string; jobId: number }[]; open: string | null }>('/api/agent/chat', json('POST', { messages })),
  /** What the resume for this job would show: its kind of role and the skill lines. Free; nothing to wait for. */
  tailored: (jobId: number) => call<{ stacks: string[]; sections: { label: string; skills: string[] }[]; unmatched: string[] }>(`/api/jobs/${jobId}/tailored`),
  notifications: () => call<Notification[]>('/api/notifications'),
  mailSync: () => call<MailSyncState>('/api/mail-sync'),
  /** One Gmail account. from (yyyy-mm-dd): read all its mail since that day again; without it, only what is new since it was last read. */
  syncMailNow: (account: number, from?: string) => call<void>(`/api/mail-sync?account=${account}${from ? `&from=${from}` : ''}`, { method: 'POST' }),
  removeMailAccount: (id: number) => call<void>(`/api/mail-accounts/${id}`, { method: 'DELETE' }),
  github: () => call<GithubStatus>('/api/github'),
  connectGithub: (token: string) => call<GithubStatus>('/api/github', json('POST', { token })),
  disconnectGithub: () => call<void>('/api/github', { method: 'DELETE' }),
  claude: () => call<ClaudeStatus>('/api/claude'),
  claudeLogin: () => call<void>('/api/claude/login', { method: 'POST' }),
  /** Reads another resume's details without keeping it. */
  readResume: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return call<ExtractedProfile>('/api/resume/read', { method: 'POST', body: form })
  },
  extractResume: () => call<ExtractedProfile>('/api/resume/extract', { method: 'POST' }),

  intakes: () => call<Intake[]>('/api/intakes'),
  addText: (text: string) => call<Intake>('/api/intakes', json('POST', { text })),
  addImage: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return call<Intake>('/api/intakes/image', { method: 'POST', body: form })
  },
  retry: (id: number) => call<Intake>(`/api/intakes/${id}/retry`, { method: 'POST' }),
  dismiss: (id: number) => call<void>(`/api/intakes/${id}`, { method: 'DELETE' }),
}
