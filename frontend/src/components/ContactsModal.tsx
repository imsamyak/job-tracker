import { useEffect, useState } from 'react'
import type { Contact } from '../api'
import { ContactForm, ContactLink } from './contacts/ContactForm'
import { PencilIcon } from './Icons'
import './ContactsModal.css'

/**
 * One popup for all of a job's contacts: the saved ones on top (each can be edited), and a last row
 * that is always an input row for adding another. Name, role, and one way to reach them.
 */
export default function ContactsModal({ jobId, contacts, onChanged, onClose }: {
  jobId: number
  contacts: Contact[]
  onChanged: () => void
  onClose: () => void
}) {
  // which saved contact is open as a form (the add row at the bottom is always open)
  const [editing, setEditing] = useState<number | null>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose])

  const saved = () => {
    setEditing(null)
    onChanged()
  }

  return (
    <div className="modal" onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className="modal__card cmodal" role="dialog" aria-label="Contacts">
        <div className="cmodal__head">
          <h2>Contacts</h2>
          <button className="icon" onClick={onClose} aria-label="Close">&times;</button>
        </div>

        <ul className="cmodal__list">
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
            <ContactForm jobId={jobId} contact={null} onSaved={onChanged} onCancel={() => undefined} focusOnOpen={contacts.length === 0} />
          </li>
        </ul>
      </div>
    </div>
  )
}
