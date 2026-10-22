import { useEffect, useRef, useState } from 'react'
import { api } from '../api'
import './AddBox.css'

const isImage = (f: File) => f.type.startsWith('image/')

/**
 * A popup with one box for everything: paste text or a link, or paste / drop / pick a screenshot.
 * It closes as soon as the job description is queued; reading it happens in the background.
 */
export default function AddBox({ onClose, onAdded }: { onClose: () => void; onAdded: () => void }) {
  const [text, setText] = useState('')
  const [image, setImage] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [dragging, setDragging] = useState(false)
  const picker = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const stage = (file: File | null) => {
    if (preview) URL.revokeObjectURL(preview)
    setImage(file)
    setPreview(file ? URL.createObjectURL(file) : null)
  }

  const takeImage = (files: FileList | File[] | null | undefined): boolean => {
    const f = files ? Array.from(files).find(isImage) : undefined
    if (!f) return false
    stage(f)
    setError(null)
    return true
  }

  const submit = async () => {
    if (busy || (!image && !text.trim())) return
    setBusy(true)
    setError(null)
    try {
      if (image) await api.addImage(image)
      else await api.addText(text)
      onAdded()
      onClose()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
      setBusy(false)
    }
  }

  return (
    <div className="modal" onMouseDown={(e) => { if (e.target === e.currentTarget && !text.trim() && !image) onClose() }}>
      <div
        className={dragging ? 'modal__card addbox addbox--drag' : 'modal__card addbox'}
        role="dialog"
        aria-label="Add a job"
        onDragOver={(e) => { e.preventDefault(); setDragging(true) }}
        onDragLeave={() => setDragging(false)}
        onDrop={(e) => {
          e.preventDefault()
          setDragging(false)
          if (!takeImage(e.dataTransfer.files)) {
            const dropped = e.dataTransfer.getData('text')
            if (dropped) setText(dropped)
          }
        }}
      >
        <div className="addbox__head">
          <h2>Add a job</h2>
          <button className="icon" onClick={onClose} aria-label="Close">&times;</button>
        </div>
        <p className="addbox__sub">Paste the job description or a link to it, or add a screenshot. It will be read in the background.</p>

        {preview ? (
          <div className="addbox__image">
            <img src={preview} alt="Screenshot to add" />
            <div>
              <div className="addbox__name">{image?.name || 'Pasted screenshot'}</div>
              <button className="link" onClick={() => stage(null)}>Remove</button>
            </div>
          </div>
        ) : (
          <textarea
            value={text}
            rows={9}
            autoFocus
            placeholder="Paste a job description or a link here, or paste / drop a screenshot"
            onChange={(e) => setText(e.target.value)}
            onPaste={(e) => {
              if (takeImage(e.clipboardData.files)) e.preventDefault()
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit()
            }}
            aria-label="Job description, link or screenshot"
          />
        )}

        {error && <div className="addbox__error">{error}</div>}

        <div className="addbox__bar">
          <button className="btn" onClick={() => picker.current?.click()}>Choose screenshot</button>
          <input
            ref={picker}
            type="file"
            accept="image/png,image/jpeg,image/gif,image/webp"
            hidden
            onChange={(e) => { takeImage(e.target.files); e.target.value = '' }}
          />
          <span className="addbox__hint">Ctrl+Enter to add</span>
          <button className="btn" onClick={onClose}>Cancel</button>
          <button className="btn btn--primary" onClick={submit} disabled={busy || (!image && !text.trim())}>
            {busy ? 'Adding...' : 'Add job'}
          </button>
        </div>
      </div>
    </div>
  )
}
