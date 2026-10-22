import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import ApplyNav, { type Section } from '../components/apply/ApplyNav'
import { useSectionNav } from '../components/useSectionNav'
import { useUndo } from '../useUndo'
import { BriefcaseIcon, CodeIcon, FileIcon, FolderIcon, GlobeIcon, LinkIcon, PhoneIcon, ResumeUploadIcon, UserIcon, UsersIcon } from '../components/Icons'
import ExportMenu from './profile/ExportMenu'
import ResumeReview from './profile/ResumeReview'
import { fromResume, loadProfile, missingFields, saveProfile, type ProfileData } from '../profileStore'
import TemplatesSection from './profile/TemplatesSection'
import { useResume } from './profile/useResume'
import { api, type ExtractedProfile } from '../api'
import ContactSection from './profile/ContactSection'
import ExperienceSection from './profile/ExperienceSection'
import LinksSection from './profile/LinksSection'
import PersonalSection from './profile/PersonalSection'
import ProfilesSection from './profile/ProfilesSection'
import ProjectsSection from './profile/ProjectsSection'
import SkillsSection from './profile/SkillsSection'
import type { SectionProps } from './profile/types'
import './profile/profile.css'
import './ProfilePage.css'

interface Block extends Section {
  hint: string
  body: (p: SectionProps & Extras) => ReactNode
}

/** What the sections that are not about the profile data itself need. */
interface Extras {
  resume: ReturnType<typeof useResume>
}

/** The page is one long scroll through these, top to bottom; the navigation on the left follows along. */
const BLOCKS: Block[] = [
  { id: 'personal', label: 'Personal', icon: <UserIcon />, hint: 'The name and summary used in everything you make.', body: (p) => <PersonalSection {...p} /> },
  { id: 'experience', label: 'Experience', icon: <BriefcaseIcon />, hint: 'Where you have worked. A tailored resume picks from this.', body: (p) => <ExperienceSection {...p} /> },
  { id: 'projects', label: 'Projects', icon: <FolderIcon />, hint: 'Things you have built.', body: (p) => <ProjectsSection {...p} /> },
  { id: 'skills', label: 'Skills', icon: <CodeIcon />, hint: 'Technologies and tools. A tailored resume leads with the ones a job asks for.', body: (p) => <SkillsSection {...p} /> },
  { id: 'contact', label: 'Contact', icon: <PhoneIcon />, hint: 'Every email and phone number you might use.', body: (p) => <ContactSection {...p} /> },
  { id: 'links', label: 'Links', icon: <LinkIcon />, hint: 'LinkedIn, GitHub, a portfolio: whatever you paste into application forms.', body: (p) => <LinksSection {...p} /> },
  { id: 'profiles', label: 'Profiles', icon: <UsersIcon />, hint: 'Choose which email, phone and links go into your resume, cover letter and referral notes.', body: (p) => <ProfilesSection {...p} /> },
  { id: 'templates', label: 'Templates', icon: <FileIcon />, hint: 'The sample resume your generated resumes are based on.', body: (p) => <TemplatesSection data={p.data} update={p.update} resume={p.resume.info} error={p.resume.error} onUpload={p.resume.upload} /> },
  {
    id: 'portfolio', label: 'Portfolio', icon: <GlobeIcon />, hint: 'A portfolio site built from your profile, kept up to date as you add to it.',
    body: () => (
      <div className="tpl__row">
        <p className="muted">Generates a portfolio from your experience, projects and skills, and updates it when they change.</p>
        <button className="btn" disabled title="Coming soon">Coming soon</button>
      </div>
    ),
  },
]

const SECTIONS: Section[] = BLOCKS.map(({ id, label, icon }) => ({ id, label, icon }))
const domId = (id: string) => `p-${id}`

const initials = (name: string) =>
  name.trim().split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w[0].toUpperCase()).join('') || '?'

/**
 * Your profile: every section on one page, saved as you type. The navigation on the left scrolls to a
 * section when clicked, and highlights the section you have scrolled to.
 */
export default function ProfilePage() {
  const { state: data, set: setData, undo, redo } = useUndo<ProfileData>(loadProfile)
  const [undoNote, setUndoNote] = useState('')
  const { active, goTo } = useSectionNav(BLOCKS.map((b) => b.id), 'p-')
  const [failed, setFailed] = useState(false)
  const resume = useResume()
  const [reading, setReading] = useState(false)
  const [readError, setReadError] = useState('')
  const [incoming, setIncoming] = useState<ExtractedProfile | null>(null) // another resume, waiting for your choices
  const picker = useRef<HTMLInputElement>(null)

  // saved at once, every time something changes
  useEffect(() => {
    setFailed(!saveProfile(data))
  }, [data])

  // Ctrl+Z undoes, Ctrl+Y or Ctrl+Shift+Z redoes (Cmd on a Mac), also while typing in a box
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (!(e.ctrlKey || e.metaKey) || e.altKey) return
      const k = e.key.toLowerCase()
      if (k !== 'z' && k !== 'y') return
      e.preventDefault()
      const redoing = k === 'y' || e.shiftKey
      const done = redoing ? redo() : undo()
      setUndoNote(done ? (redoing ? 'Redone' : 'Undone') : redoing ? 'Nothing to redo' : 'Nothing to undo')
      setTimeout(() => setUndoNote(''), 1500)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [undo, redo])

  const update = useCallback((change: (d: ProfileData) => ProfileData) => setData((d) => change(d)), [])

  const name = data.personal.name.trim()
  const missing = missingFields(data)
  const hasData = !!(name || data.experience.length || data.projects.length || data.contact.emails.length)

  // the resume is kept either way; an empty profile is also filled from it (a filled one is never overwritten)
  const addResume = async (file: File) => {
    setReadError('')
    setReading(true) // from the moment it is chosen: saving it and reading it both take a while
    // the first resume you add is kept as the sample; later ones are only read, so the sample stays as it is
    if (!resume.info?.present && !(await resume.upload(file))) {
      setReading(false)
      return
    }
    try {
      const x = await api.readResume(file)
      if (hasData) {
        setIncoming(x)
        return
      }
      const filled = fromResume(x)
      setData((d) => ({ ...filled, personal: { ...filled.personal, notice: d.personal.notice }, coverLetter: d.coverLetter, resumeTemplate: d.resumeTemplate, notes: d.notes }))
    } catch (e) {
      setReadError(e instanceof Error ? e.message : 'Could not read the resume.')
    } finally {
      setReading(false)
    }
  }

  return (
    <div className="profile">
      <a className="back" href="#/">&larr; All jobs</a>

      <header className="profile__head">
        <span className="profile__avatar" aria-hidden>{name ? initials(data.personal.name) : <UserIcon />}</span>
        <div className="profile__who">
          <h1>{name || 'Your profile'}</h1>
          <p className="muted">{data.personal.headline.trim() || 'Fill in the sections below. They are saved as you type.'}</p>
        </div>
        {failed && <span className="profile__state profile__state--bad">Could not save in this browser</span>}
        <ExportMenu data={data} />
        {reading && <span className="profile__busy" role="status">Reading your resume...</span>}
        {undoNote && <span className="profile__busy" role="status">{undoNote}</span>}
        <input ref={picker} type="file" accept="application/pdf,.pdf" hidden
          onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) void addResume(f) }} />
        <button className="btn btn--icon" disabled={reading} onClick={() => picker.current?.click()}
          title={reading ? 'Reading your resume...' : resume.info?.present ? `Replace resume (${resume.info.filename})` : 'Upload resume'}
          aria-label="Upload resume">
          {reading ? <span className="spinner" aria-hidden /> : <ResumeUploadIcon />}
        </button>
      </header>

      <div className="pwin">
        <ApplyNav sections={SECTIONS} active={active} onSelect={goTo} />
        <div className="pwin__page">
          {missing.length > 0 && (
            <p className="profile__missing">Profile not complete. Still needed: {missing.join(', ')}.</p>
          )}
          {(readError || resume.error) && <p className="profile__missing">{readError || resume.error}</p>}
          {BLOCKS.map((b) => (
            <section key={b.id} id={domId(b.id)} className="card psec">
              <header className="psec__head">
                <h2>{b.label}</h2>
                <p className="muted">{b.hint}</p>
              </header>
              {b.body({ data, update, resume })}
            </section>
          ))}
          {/* room at the end, so the last sections can be scrolled up to the top as well */}
          <div className="pwin__end" aria-hidden />
        </div>
      </div>
      {incoming && (
        <ResumeReview current={data} incoming={incoming} onCancel={() => setIncoming(null)} onApply={(next) => { setData(next); setIncoming(null) }} />
      )}
    </div>
  )
}
