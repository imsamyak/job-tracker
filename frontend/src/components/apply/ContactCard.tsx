import { useCallback, useEffect, useState } from 'react'
import { api, type Contact } from '../../api'
import { ContactForm, ContactLink } from '../contacts/ContactForm'
import { PencilIcon } from '../Icons'

/**
 * The job's HR / recruiter contacts, right in the apply window: the saved ones on top (each can be
 * edited) and an always-open row at the bottom to add another, so a contact can be saved the moment
 * you find one.
 */
export default function ContactCard({ jobId }: { jobId: number }) {
  const [contacts, setContacts] = useState<Contact[]>([])
  const [editing, setEditing] = useState<number | null>(null)

  const load = useCallback(() => {
    api.contacts(jobId).then(setContacts).catch(() => setContacts([]))
  }, [jobId])

  useEffect(() => {
    load()
  }, [load])

  const saved = () => {
    setEditing(null)
    load()
  }

  return (
    <section className="card acard">
      <header className="acard__head">
        <h2>Contacts</h2>
        <span className="muted">{contacts.length === 0 ? 'None saved yet' : `${contacts.length} saved`}</span>
      </header>

      <ul className="clist">
        {contacts.map((c) => (
          <li key={c.id} className="crow">
            {editing === c.id ? (
              <ContactForm jobId={jobId} contact={c} onSaved={saved} onCancel={() => setEditing(null)} />
            ) : (
              <>
                <div className="crow__main">
                  <div className="crow__name">
                    {c.name}
                    {c.role && <span className="crow__role"> &middot; {c.role}</span>}
                  </div>
                  {c.contact && <div className="crow__link"><ContactLink value={c.contact} /></div>}
                </div>
                <button className="iconbtn iconbtn--apply" title="Edit" aria-label={`Edit ${c.name}`} onClick={() => setEditing(c.id)}>
                  <PencilIcon />
                </button>
              </>
            )}
          </li>
        ))}
        <li className="crow crow--add">
          <ContactForm jobId={jobId} contact={null} onSaved={load} onCancel={() => undefined} />
        </li>
      </ul>
    </section>
  )
}
