import { newId, type ProfileSet, type Use } from '../../profileStore'
import type { SectionProps } from './types'

const USES: { id: Use; label: string }[] = [
  { id: 'resume', label: 'Resume' },
  { id: 'coverLetter', label: 'Cover letter' },
  { id: 'referral', label: 'Referral note' },
]

/**
 * A profile is a choice of which email, phone and links to show. Make as many as you need (a main one,
 * one for referrals...), then say which one each kind of document uses.
 */
export default function ProfilesSection({ data, update }: SectionProps) {
  const { emails, phones, links } = data.contact
  const noContact = emails.length + phones.length + links.length === 0

  const change = (id: string, patch: Partial<ProfileSet>) =>
    update((d) => ({ ...d, profiles: d.profiles.map((p) => (p.id === id ? { ...p, ...patch } : p)) }))

  const add = () =>
    update((d) => ({
      ...d,
      profiles: [...d.profiles, {
        id: newId(),
        name: d.profiles.length === 0 ? 'Main' : `Profile ${d.profiles.length + 1}`,
        emailId: d.contact.emails[0]?.id ?? '',
        phoneId: d.contact.phones[0]?.id ?? '',
        linkIds: d.contact.links.map((l) => l.id),
      }],
    }))

  const remove = (id: string) =>
    update((d) => ({
      ...d,
      profiles: d.profiles.filter((p) => p.id !== id),
      use: Object.fromEntries(Object.entries(d.use).map(([k, v]) => [k, v === id ? '' : v])) as typeof d.use,
    }))

  return (
    <div className="pfs">
      {noContact && <p className="muted">Add your emails, phone numbers and links under Contact first, then choose among them here.</p>}

      {data.profiles.map((p) => (
        <div key={p.id} className="pf">
          <div className="pf__head">
            <input className="pf__name" value={p.name} onChange={(e) => change(p.id, { name: e.target.value })} aria-label="Profile name" />
            <button className="iconbtn iconbtn--delete" title="Delete profile" aria-label={`Delete ${p.name}`} onClick={() => remove(p.id)}>&times;</button>
          </div>
          <div className="pgrid">
            <label>Email
              <select value={p.emailId} onChange={(e) => change(p.id, { emailId: e.target.value })}>
                <option value="">None</option>
                {emails.map((e) => <option key={e.id} value={e.id}>{e.label ? `${e.label} · ${e.value}` : e.value}</option>)}
              </select>
            </label>
            <label>Phone
              <select value={p.phoneId} onChange={(e) => change(p.id, { phoneId: e.target.value })}>
                <option value="">None</option>
                {phones.map((e) => <option key={e.id} value={e.id}>{e.label ? `${e.label} · ${e.value}` : e.value}</option>)}
              </select>
            </label>
          </div>
          {links.length > 0 && (
            <div className="pf__links">
              <span className="pf__legend">Links to show</span>
              {links.map((l) => (
                <label key={l.id} className="pf__check">
                  <input
                    type="checkbox"
                    checked={p.linkIds.includes(l.id)}
                    onChange={(e) => change(p.id, { linkIds: e.target.checked ? [...p.linkIds, l.id] : p.linkIds.filter((x) => x !== l.id) })}
                  />
                  {l.label || l.value}
                </label>
              ))}
            </div>
          )}
        </div>
      ))}

      <button className="btn" title="Add profile" aria-label="Add profile" onClick={add} disabled={noContact}>+ Add</button>

      {data.profiles.length > 0 && (
        <div className="uses">
          <h3>Which profile each document uses</h3>
          <div className="pgrid pgrid--three">
            {USES.map((u) => (
              <label key={u.id}>{u.label}
                <select value={data.use[u.id]} onChange={(e) => update((d) => ({ ...d, use: { ...d.use, [u.id]: e.target.value } }))}>
                  <option value="">First email, phone and links</option>
                  {data.profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
                </select>
              </label>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
