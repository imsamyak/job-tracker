import { useEffect, useState } from 'react'
import type { Job } from '../../api'
import { loadProfile, resolveFor } from '../../profileStore'
import { fillTemplate } from '../../templates'
import { CheckIcon, CopyIcon } from '../Icons'
import { useCopy } from './useCopy'

type Kind = 'referral' | 'hr'
type Variant = 'short' | 'full'

const KINDS: { id: Kind; label: string }[] = [
  { id: 'referral', label: 'Referral' },
  { id: 'hr', label: 'HR note' },
]

/**
 * Two notes with the job filled in: one asking someone at the company for a referral, and one for HR, sent
 * right after applying. The wording comes from your templates (profile, Templates); your own details come
 * from your profile, and anything not saved there stays as a [bracket] to fill in by hand. The HR note goes out
 * under the email the application was made with (the Resume profile); the referral note has its own choice.
 */
function noteFor(job: Job, kind: Kind, variant: Variant): string {
  const profile = loadProfile()
  const me = resolveFor(profile, kind === 'hr' ? 'resume' : 'referral')
  const template = profile.notes[`${kind}${variant === 'short' ? 'Short' : 'Full'}` as keyof typeof profile.notes]
  return fillTemplate(template, {
    name: me.name || '[your name]',
    headline: me.headline || '[your current role]',
    experience: me.experience || '[X years]',
    email: me.email || '[your email]',
    linkedin: me.linkedin || '[your profile]',
    notice: me.notice,
    company: job.company,
    role: job.role,
    id: job.postingId ? ` (Job ID ${job.postingId})` : '',
  })
}

export default function ReferralCard({ job }: { job: Job }) {
  const [kind, setKind] = useState<Kind>('referral')
  const [variant, setVariant] = useState<Variant>('short')
  const [text, setText] = useState(() => noteFor(job, kind, variant))
  const { copied, copy } = useCopy()

  // switching tabs loads that template; typing edits it freely
  useEffect(() => setText(noteFor(job, kind, variant)), [job, kind, variant])

  return (
    <section className="card acard">
      <header className="acard__head">
        <div className="seg" role="tablist" aria-label="Kind of note">
          {KINDS.map((k) => (
            <button key={k.id} role="tab" aria-selected={kind === k.id} className={kind === k.id ? 'seg__on' : ''} onClick={() => setKind(k.id)}>
              {k.label}
            </button>
          ))}
        </div>
        <div className="seg" role="tablist" aria-label="Note length">
          {(['short', 'full'] as Variant[]).map((v) => (
            <button key={v} role="tab" aria-selected={variant === v} className={variant === v ? 'seg__on' : ''} onClick={() => setVariant(v)}>
              {v === 'short' ? 'Short' : 'Full'}
            </button>
          ))}
        </div>
      </header>
      <textarea
        className="note"
        value={text}
        onChange={(e) => setText(e.target.value)}
        rows={variant === 'short' ? 6 : 13}
        aria-label={kind === 'hr' ? 'HR note' : 'Referral note'}
      />
      <div className="acard__foot">
        <span className="muted">{text.includes('[') ? <>Fill in the [brackets] or save them in your <a href="#/profile">profile</a>, then copy.</> : 'Ready to copy.'}</span>
        <button className="btn" onClick={() => copy('note', text)}>
          {copied === 'note' ? <><CheckIcon /> Copied</> : <><CopyIcon /> Copy</>}
        </button>
      </div>
    </section>
  )
}
