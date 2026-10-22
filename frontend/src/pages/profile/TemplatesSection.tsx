import { useState } from 'react'
import type { ResumeInfo } from '../../api'
import { UploadIcon } from '../../components/Icons'
import { DEFAULT_COVER_LETTER, type ResumeTemplate } from '../../profileStore'
import { DEFAULT_NOTES, type Notes } from '../../templates'
import AutoText from './AutoText'
import ResumePicker from './ResumePicker'
import type { SectionProps } from './types'

const LOOKS: { id: ResumeTemplate; name: string }[] = [
  { id: 'classic', name: 'Classic' },
  { id: 'modern', name: 'Modern' },
  { id: 'compact', name: 'Compact' },
]

type Variant = 'Short' | 'Full'

/** One note with a Short and a Full text, shown one at a time. */
function NoteTemplate({ title, short, full, notes, onChange }: {
  title: string
  short: keyof Notes
  full: keyof Notes
  notes: Notes
  onChange: (key: keyof Notes, value: string) => void
}) {
  const [variant, setVariant] = useState<Variant>('Short')
  const key = variant === 'Short' ? short : full
  return (
    <>
      <div className="tpl__row">
        <strong>{title}</strong>
        <div className="tpl__tools">
          {notes[key] !== DEFAULT_NOTES[key] && <button className="btn" onClick={() => onChange(key, DEFAULT_NOTES[key])}>Reset</button>}
          <div className="seg" role="tablist" aria-label={`${title} length`}>
            {(['Short', 'Full'] as Variant[]).map((v) => (
              <button key={v} role="tab" aria-selected={variant === v} className={variant === v ? 'seg__on' : ''} onClick={() => setVariant(v)}>{v}</button>
            ))}
          </div>
        </div>
      </div>
      <AutoText value={notes[key]} label={`${title}, ${variant.toLowerCase()}`} onChange={(v) => onChange(key, v)} />
    </>
  )
}

/** The templates: a sample resume, the cover letter, and the referral and HR notes. */
export default function TemplatesSection({ data, update, resume, error, onUpload }: SectionProps & {
  resume: ResumeInfo | null
  error: string
  onUpload: (f: File) => Promise<boolean>
}) {
  const setNote = (key: keyof Notes, value: string) => update((d) => ({ ...d, notes: { ...d.notes, [key]: value } }))

  return (
    <div className="tpl">
      <div className="tpl__row">
        <div>
          <strong>Resume template</strong>
          <p className="muted">
            {resume?.present
              ? <>Your sample resume: <a href="/api/resume/file" target="_blank" rel="noreferrer">{resume.filename}</a>. Generated resumes follow its layout.</>
              : 'Add a sample resume (PDF). Generated resumes follow its layout.'}
          </p>
        </div>
        <ResumePicker label={resume?.present ? 'Replace sample resume' : 'Add sample resume'} icon={<UploadIcon />} onPick={(f) => void onUpload(f)} />
      </div>
      {error && <p className="tpl__error">{error}</p>}
      <div className="looks" role="radiogroup" aria-label="Resume template">
        {LOOKS.map((l) => (
          <button key={l.id} role="radio" aria-checked={false} className="look" disabled>
            <span className={`look__page look__page--${l.id}`} aria-hidden>
              <i /><i /><i /><i /><i />
            </span>
            <strong>{l.name}</strong>
            <span className="muted">Coming soon</span>
          </button>
        ))}
      </div>

      <div className="tpl__row">
        <strong>Cover letter template</strong>
        {data.coverLetter !== DEFAULT_COVER_LETTER && (
          <button className="btn" onClick={() => update((d) => ({ ...d, coverLetter: DEFAULT_COVER_LETTER }))}>Reset</button>
        )}
      </div>
      <AutoText value={data.coverLetter} label="Cover letter template" onChange={(v) => update((d) => ({ ...d, coverLetter: v }))} />

      <NoteTemplate title="Referral note template" short="referralShort" full="referralFull" notes={data.notes} onChange={setNote} />
      <NoteTemplate title="HR note template" short="hrShort" full="hrFull" notes={data.notes} onChange={setNote} />
    </div>
  )
}
