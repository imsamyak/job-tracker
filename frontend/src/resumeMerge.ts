import type { ExtractedProfile } from './api'
import { newId, type Labeled, type ProfileData, type SkillGroup } from './profileStore'

export type Choice = 'merge' | 'replace' | 'ignore'
export type Field = 'name' | 'headline' | 'experience' | 'location' | 'about'
export type ListKey = 'experience' | 'projects' | 'skills' | 'emails' | 'phones' | 'links'

export interface Choices {
  /** For each personal detail the new resume has: keep what you have, or use the new one. */
  personal: Record<Field, 'keep' | 'new'>
  lists: Record<ListKey, Choice>
}

export const FIELDS: { key: Field; label: string }[] = [
  { key: 'name', label: 'Name' },
  { key: 'headline', label: 'Headline' },
  { key: 'experience', label: 'Experience' },
  { key: 'location', label: 'Location' },
  { key: 'about', label: 'About' },
]

const norm = (s: string) => s.toLowerCase().replace(/[^\p{L}\p{Nd}]+/gu, ' ').trim()

const keys = {
  experience: (e: { company: string; role: string }) => `${norm(e.company)}|${norm(e.role)}`,
  projects: (p: { name: string }) => norm(p.name),
  emails: (c: { value: string }) => c.value.trim().toLowerCase(),
  phones: (c: { value: string }) => c.value.replace(/\D+/g, ''),
  links: (c: { value: string }) => c.value.trim().toLowerCase().replace(/^https?:\/\/(www\.)?/, '').replace(/\/$/, ''),
}

/** How many of the new resume's items are not in the profile yet, for each list. */
export function counts(d: ProfileData, x: ExtractedProfile): Record<ListKey, { incoming: number; fresh: number }> {
  const c = (have: string[], incoming: string[]) => ({ incoming: incoming.length, fresh: incoming.filter((k) => k && !have.includes(k)).length })
  return {
    experience: c(d.experience.map(keys.experience), x.experience.map(keys.experience)),
    projects: c(d.projects.map(keys.projects), x.projects.map(keys.projects)),
    skills: c(d.skills.flatMap((g) => g.items.map(norm)), x.skills.flatMap((g) => g.items.map(norm))),
    emails: c(d.contact.emails.map(keys.emails), x.contact.emails.map(keys.emails)),
    phones: c(d.contact.phones.map(keys.phones), x.contact.phones.map(keys.phones)),
    links: c(d.contact.links.map(keys.links), x.contact.links.map(keys.links)),
  }
}

/** Sensible starting choices: add what is new, keep what you have, and fill in what is empty. */
export function defaultChoices(d: ProfileData, x: ExtractedProfile): Choices {
  const personal = {} as Choices['personal']
  for (const f of FIELDS) personal[f.key] = !d.personal[f.key].trim() && x.personal[f.key].trim() ? 'new' : 'keep'
  const lists = { experience: 'merge', projects: 'merge', skills: 'merge', emails: 'merge', phones: 'merge', links: 'merge' } as Record<ListKey, Choice>
  return { personal, lists }
}

function mergeLabeled(have: Labeled[], incoming: { label: string; value: string }[], choice: Choice, key: (c: { value: string }) => string): Labeled[] {
  if (choice === 'ignore') return have
  const add = (list: { label: string; value: string }[]) => list.map((i) => ({ id: newId(), label: i.label, value: i.value }))
  if (choice === 'replace') return add(incoming)
  const seen = new Set(have.map(key))
  return [...have, ...add(incoming.filter((i) => !seen.has(key(i))))]
}

/** Merge adds skills the groups do not have yet (into the group of the same name, or as a new group); replace swaps all groups. */
function mergeSkills(have: SkillGroup[], incoming: ExtractedProfile['skills'], choice: Choice): SkillGroup[] {
  if (choice === 'ignore') return have
  if (choice === 'replace') return incoming.map((g) => ({ id: newId(), name: g.name, items: g.items }))
  const out = have.map((g) => ({ ...g, items: [...g.items] }))
  const known = new Set(out.flatMap((g) => g.items.map(norm)))
  for (const g of incoming) {
    const fresh = g.items.filter((i) => !known.has(norm(i)))
    if (fresh.length === 0) continue
    fresh.forEach((i) => known.add(norm(i)))
    const same = out.find((o) => norm(o.name) === norm(g.name))
    if (same) same.items.push(...fresh)
    else out.push({ id: newId(), name: g.name, items: fresh })
  }
  return out
}

/** The profile after applying the choices. Replacing contact details drops the profile sets, as those pointed at the old entries. */
export function applyResume(d: ProfileData, x: ExtractedProfile, ch: Choices): ProfileData {
  const personal = { ...d.personal }
  for (const f of FIELDS) if (ch.personal[f.key] === 'new' && x.personal[f.key].trim()) personal[f.key] = x.personal[f.key]

  const list = <T, N>(have: T[], incoming: N[], choice: Choice, key: (i: any) => string, make: (n: N) => T): T[] => {
    if (choice === 'ignore') return have
    if (choice === 'replace') return incoming.map(make)
    const seen = new Set(have.map(key))
    return [...have, ...incoming.filter((n) => !seen.has(key(n))).map(make)]
  }

  const experience = list(d.experience, x.experience, ch.lists.experience, keys.experience, (e) => ({ id: newId(), ...e }))
  const projects = list(d.projects, x.projects, ch.lists.projects, keys.projects, (p) => ({ id: newId(), name: p.name, links: p.links.map((l) => l.value), stack: p.stack, details: p.details }))
  const skills = mergeSkills(d.skills, x.skills, ch.lists.skills)
  const contact = {
    emails: mergeLabeled(d.contact.emails, x.contact.emails, ch.lists.emails, keys.emails),
    phones: mergeLabeled(d.contact.phones, x.contact.phones, ch.lists.phones, keys.phones),
    links: mergeLabeled(d.contact.links, x.contact.links, ch.lists.links, keys.links),
  }
  const replacedContact = (['emails', 'phones', 'links'] as ListKey[]).some((k) => ch.lists[k] === 'replace')
  return {
    ...d,
    personal,
    experience,
    projects,
    skills,
    contact,
    profiles: replacedContact ? [] : d.profiles,
    use: replacedContact ? { resume: '', coverLetter: '', referral: '' } : d.use,
  }
}
