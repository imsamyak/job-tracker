import type { Experience } from '../../profileStore'
import EntryList, { type FieldDef } from './EntryList'
import type { SectionProps } from './types'

const FIELDS: FieldDef<Experience>[] = [
  { key: 'company', label: 'Company', placeholder: 'Company name', half: true },
  { key: 'role', label: 'Role', placeholder: 'Software Engineer', half: true },
  { key: 'start', label: 'From', placeholder: 'Jan 2023', half: true },
  { key: 'end', label: 'To', placeholder: 'Mar 2025', half: true, showIf: (v) => !v.current },
  { key: 'current', label: 'I work here now', type: 'check' },
  { key: 'details', label: 'What you did', type: 'textarea', rewrite: { kind: 'EXPERIENCE', context: (e) => [e.role, e.company && `at ${e.company}`].filter(Boolean).join(' ') }, placeholder: 'One achievement per line.' },
]

/** Where you have worked. The details are what a tailored resume picks from. */
export default function ExperienceSection({ data, update }: SectionProps) {
  return (
    <EntryList<Experience>
      items={data.experience}
      onChange={(next) => update((d) => ({ ...d, experience: next }))}
      fields={FIELDS}
      requiredKey="company"
      blank={() => ({ company: '', role: '', start: '', end: '', current: false, details: '' })}
      summary={(e) => ({
        title: e.role ? `${e.role} · ${e.company}` : e.company,
        subtitle: [e.start, e.current ? 'Present' : e.end].filter(Boolean).join(' – '),
        body: e.details,
      })}
      addLabel="+ Add" addTitle="Add experience"
      empty="No experience added yet."
    />
  )
}
