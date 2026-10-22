import { useState } from 'react'
import { api } from '../../api'
import type { Project } from '../../profileStore'
import EntryList, { type FieldDef } from './EntryList'
import type { SectionProps } from './types'

const FIELDS: FieldDef<Project>[] = [
  { key: 'name', label: 'Project', placeholder: 'Project name', half: true },
  { key: 'links', label: 'Links (one per line)', type: 'lines', placeholder: 'github.com/you/project\nyour-demo.com\nyoutube.com/...' },
  { key: 'stack', label: 'Built with', placeholder: 'React, Spring Boot, PostgreSQL' },
  { key: 'details', label: 'What it does', type: 'textarea', rewrite: { kind: 'PROJECT', context: (p) => `${p.name}${p.stack ? ` (${p.stack})` : ''}` }, placeholder: 'One or two lines on what it is and what you did.' },
]

/** Reads a link (a GitHub repository or any page about the project) and fills the form with what it finds. */
function LinkReader({ draft, setDraft }: { draft: Project; setDraft: (p: Project) => void }) {
  const [url, setUrl] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const read = async () => {
    if (!url.trim() || busy) return
    setBusy(true)
    setError('')
    try {
      const p = await api.projectFromLink(url.trim())
      // what was already typed is kept; only empty fields are filled
      setDraft({
        ...draft,
        name: draft.name || p.name,
        links: draft.links.length > 0 ? draft.links : p.link ? [p.link] : [],
        stack: draft.stack || p.stack,
        details: draft.details || p.details,
      })
      setUrl('')
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not read that link.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="linkread">
      <div className="ghadd">
        <input
          value={url}
          placeholder="Fill from a link: GitHub or any page about the project"
          disabled={busy}
          onChange={(e) => setUrl(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); void read() } }}
        />
        <button type="button" className="btn" disabled={busy || !url.trim()} onClick={() => void read()}>
          {busy ? 'Reading...' : 'Read'}
        </button>
      </div>
      {error && <p className="ghadd__error">{error}</p>}
      <p className="muted linkread__or">or fill in the details yourself</p>
    </div>
  )
}

/** Things you have built, for the resume. */
export default function ProjectsSection({ data, update }: SectionProps) {
  return (
    <EntryList<Project>
      items={data.projects}
      onChange={(next) => update((d) => ({ ...d, projects: next }))}
      fields={FIELDS}
      requiredKey="name"
      blank={() => ({ name: '', links: [], stack: '', details: '' })}
      summary={(p) => ({ title: p.name, subtitle: p.stack, body: p.details, links: p.links })}
      addLabel="+ Add" addTitle="Add project"
      empty="No projects added yet."
      helper={(draft, setDraft) => <LinkReader draft={draft} setDraft={setDraft} />}
    />
  )
}
