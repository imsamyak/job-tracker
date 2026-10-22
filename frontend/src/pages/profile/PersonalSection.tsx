import GrowTextarea from './GrowTextarea'
import RewriteButton from './RewriteButton'
import { SUMMARY_VARIANTS } from '../../profileStore'
import type { SectionProps } from './types'

/** Who you are, in a few lines. Used as the name, headline and summary in everything you generate. */
export default function PersonalSection({ data, update }: SectionProps) {
  const p = data.personal
  const set = (key: keyof typeof p) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) =>
    update((d) => ({ ...d, personal: { ...d.personal, [key]: e.target.value } }))

  return (
    <div className="pgrid">
      <label>Full name<input value={p.name} onChange={set('name')} placeholder="Your full name" /></label>
      <label>Headline<input value={p.headline} onChange={set('headline')} placeholder="Software Engineer at Company" /></label>
      <label>Experience<input value={p.experience} onChange={set('experience')} placeholder="2 years" /></label>
      <label>Location<input value={p.location} onChange={set('location')} placeholder="Pune, MH" /></label>
      <label>Notice period<input value={p.notice} onChange={set('notice')} placeholder="30 days, or leave empty" /></label>
      <label className="pgrid__full">About you
        <GrowTextarea rows={5} value={p.about} onChange={set('about')} placeholder="A short summary: what you do, what you are good at, what you are looking for." />
      </label>
      <div className="pgrid__full">
        <RewriteButton kind="ABOUT" text={p.about} context={[p.headline, p.experience && `${p.experience} of experience`].filter(Boolean).join(', ')}
                       onUse={(next) => update((d) => ({ ...d, personal: { ...d.personal, about: next } }))} />
      </div>
      <div className="pgrid__full summaries">
        <h3>Summary per kind of role</h3>
        <p className="muted">The job's main stack picks one for its resume; an empty one falls back to About you.</p>
        {SUMMARY_VARIANTS.map((v) => (
          <label key={v}>{v}
            <GrowTextarea rows={3} value={data.summaries[v]} placeholder={`Summary for ${v} roles`}
                          onChange={(e) => update((d) => ({ ...d, summaries: { ...d.summaries, [v]: e.target.value } }))} />
            <RewriteButton kind="ABOUT" text={data.summaries[v]} context={`${v} roles`}
                           onUse={(next) => update((d) => ({ ...d, summaries: { ...d.summaries, [v]: next } }))} />
          </label>
        ))}
      </div>
    </div>
  )
}
