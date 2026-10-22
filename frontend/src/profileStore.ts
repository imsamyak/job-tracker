/**
 * Everything about you that the app uses to tailor a resume, a cover letter or a referral note.
 * Kept in this browser only (there is no backend for it yet), so it never leaves your computer.
 */

import type { ExtractedProfile } from './api'
import { DEFAULT_NOTES, type Notes } from './templates'

export interface Labeled { id: string; label: string; value: string }

export interface Experience {
  id: string
  company: string
  role: string
  start: string
  end: string
  current: boolean
  details: string
}

/** Skills under one heading, as a resume groups them. */
export interface SkillGroup { id: string; name: string; items: string[] }

export interface Project { id: string; name: string; /** Any number: the repository, a live demo, a video... */ links: string[]; stack: string; details: string }

/** A named choice of which email, phone and links to show, e.g. "Main" or "Referral". */
export interface ProfileSet { id: string; name: string; emailId: string; phoneId: string; linkIds: string[] }

export type ResumeTemplate = 'classic' | 'modern' | 'compact'

export type Use = 'resume' | 'coverLetter' | 'referral'

/** The kinds of role a summary can be written for; the job's main stack picks one. */
export const SUMMARY_VARIANTS = ['backend', 'frontend', 'full-stack', 'cloud'] as const
export type SummaryVariant = (typeof SUMMARY_VARIANTS)[number]

export interface ProfileData {
  personal: { name: string; headline: string; experience: string; location: string; about: string; notice: string }
  /** A summary per kind of role; one left empty falls back to the About text. */
  summaries: Record<SummaryVariant, string>
  experience: Experience[]
  projects: Project[]
  skills: SkillGroup[]
  contact: { emails: Labeled[]; phones: Labeled[]; links: Labeled[] }
  profiles: ProfileSet[]
  /** Which profile each kind of document uses ('' = the first email, phone and links). */
  use: Record<Use, string>
  /** The cover letter text; {name} {role} {company} {experience} are filled in per job. */
  coverLetter: string
  /** The look generated resumes use. */
  resumeTemplate: ResumeTemplate
  /** The referral and HR note texts, with {placeholders} filled in per job. */
  notes: Notes
}

export const DEFAULT_COVER_LETTER = `Dear Hiring Team at {company},

I'm excited to apply for the {role} position. As {headline} with {experience} of experience, I enjoy turning requirements into software that is reliable, well tested and easy to maintain, and this role looks like a strong match for that.

What draws me to {company} is the chance to work on meaningful problems with a team that cares about quality. I take ownership of my work from design through to production, I learn quickly, and I communicate clearly with the people I build for.

I would welcome the chance to discuss how I can contribute to {company}. Thank you for your time and consideration. You can reach me at {email}.

Best regards,
{name}`

/** The text this used to be. A saved letter that was never edited still holds it, and is upgraded on load. */
const OLD_DEFAULT_COVER_LETTER = `Dear Hiring Team at {company},

I am writing to apply for the {role} position. I am {name}, with {experience} of experience, and the role is a strong match for my background.

I would welcome the chance to talk about how I can contribute to {company}.

Regards,
{name}`

export const EMPTY_PROFILE: ProfileData = {
  personal: { name: '', headline: '', experience: '', location: '', about: '', notice: '' },
  summaries: { backend: '', frontend: '', 'full-stack': '', cloud: '' },
  experience: [],
  projects: [],
  skills: [],
  contact: { emails: [], phones: [], links: [] },
  profiles: [],
  use: { resume: '', coverLetter: '', referral: '' },
  coverLetter: DEFAULT_COVER_LETTER,
  resumeTemplate: 'classic',
  notes: DEFAULT_NOTES,
}

const KEY = 'jobtracker.profile.v2'

export const newId = () => crypto.randomUUID()

/** Skills used to be one flat list: it becomes a single group. */
function loadSkills(raw: unknown): SkillGroup[] {
  if (!Array.isArray(raw)) return []
  if (raw.length > 0 && typeof raw[0] === 'string') return [{ id: newId(), name: 'Skills', items: raw as string[] }]
  return (raw as Partial<SkillGroup>[]).map((g) => ({ id: g.id ?? newId(), name: g.name ?? '', items: g.items ?? [] }))
}

/** Missing pieces fall back to empty ones, so an older or partly saved profile still loads. */
export function loadProfile(): ProfileData {
  try {
    const raw = localStorage.getItem(KEY)
    if (!raw) return EMPTY_PROFILE
    const d = JSON.parse(raw)
    return {
      personal: { ...EMPTY_PROFILE.personal, ...d.personal },
      summaries: { ...EMPTY_PROFILE.summaries, ...d.summaries },
      experience: d.experience ?? [],
      // a project used to have one link: it becomes the first of its links
      projects: (d.projects ?? []).map((p: Partial<Project> & { link?: string }) => ({ ...p, links: p.links ?? (p.link ? [p.link] : []) })),
      skills: loadSkills(d.skills),
      contact: { ...EMPTY_PROFILE.contact, ...d.contact },
      profiles: d.profiles ?? [],
      use: { ...EMPTY_PROFILE.use, ...d.use },
      coverLetter: typeof d.coverLetter === 'string' && d.coverLetter !== OLD_DEFAULT_COVER_LETTER ? d.coverLetter : DEFAULT_COVER_LETTER,
      resumeTemplate: ['classic', 'modern', 'compact'].includes(d.resumeTemplate) ? d.resumeTemplate : 'classic',
      notes: { ...DEFAULT_NOTES, ...d.notes },
    }
  } catch {
    return EMPTY_PROFILE // blocked or broken storage: behave as if nothing is saved
  }
}

export function saveProfile(d: ProfileData): boolean {
  try {
    localStorage.setItem(KEY, JSON.stringify(d))
    return true
  } catch {
    return false
  }
}

export interface Resolved {
  name: string
  headline: string
  experience: string
  /** Notice period, e.g. 30 days; empty when you have none to mention. */
  notice: string
  email: string
  phone: string
  /** The LinkedIn address among the chosen links, if any. */
  linkedin: string
  links: Labeled[]
}

/**
 * The details to put in one kind of document: the name and headline from Personal, and the email,
 * phone and links from the profile chosen for that kind (or the first of each when none is chosen).
 */
export function resolveFor(d: ProfileData, use: Use): Resolved {
  const set = d.profiles.find((p) => p.id === d.use[use])
  const email = (set ? d.contact.emails.find((e) => e.id === set.emailId) : undefined) ?? (set ? undefined : d.contact.emails[0])
  const phone = (set ? d.contact.phones.find((p) => p.id === set.phoneId) : undefined) ?? (set ? undefined : d.contact.phones[0])
  const links = set ? d.contact.links.filter((l) => set.linkIds.includes(l.id)) : d.contact.links
  const linkedin = links.find((l) => /linkedin/i.test(l.label + l.value))
  return {
    name: d.personal.name,
    headline: d.personal.headline,
    experience: d.personal.experience,
    notice: d.personal.notice,
    email: email?.value ?? '',
    phone: phone?.value ?? '',
    linkedin: linkedin?.value ?? '',
    links,
  }
}

/** What is still missing before the profile is usable for applications. Empty when complete. */
export function missingFields(d: ProfileData): string[] {
  const out: string[] = []
  if (!d.personal.name.trim()) out.push('name')
  if (!d.personal.headline.trim()) out.push('headline')
  if (!d.contact.emails.some((e) => e.value.trim())) out.push('an email')
  if (!d.contact.phones.some((p) => p.value.trim())) out.push('a phone number')
  return out
}

/** Replaces the profile's details with those read from a resume. Which email/phone each document uses is reset, as those ids are new. */
export function fromResume(x: ExtractedProfile): ProfileData {
  const labeled = (items: { label: string; value: string }[]): Labeled[] =>
    items.map((i) => ({ id: newId(), label: i.label, value: i.value }))
  return {
    personal: { ...x.personal, notice: '' },
    summaries: { ...EMPTY_PROFILE.summaries },
    experience: x.experience.map((e) => ({ id: newId(), ...e })),
    projects: x.projects.map((p) => ({ id: newId(), name: p.name, links: p.links.map((l) => l.value), stack: p.stack, details: p.details })),
    skills: x.skills.map((g) => ({ id: newId(), name: g.name, items: g.items })),
    contact: { emails: labeled(x.contact.emails), phones: labeled(x.contact.phones), links: labeled(x.contact.links) },
    profiles: [],
    use: { ...EMPTY_PROFILE.use },
    coverLetter: DEFAULT_COVER_LETTER,
    resumeTemplate: 'classic',
    notes: DEFAULT_NOTES,
  }
}
