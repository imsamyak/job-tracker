import { useEffect, useState } from 'react'
import type { Job } from '../api'
import './ConfirmDelete.css'

/** Asks once more before a job is deleted. Closes with Cancel, Esc or a click outside. */
export default function ConfirmDelete({ job, onCancel, onConfirm }: {
  job: Job
  onCancel: () => void
  onConfirm: () => Promise<void>
}) {
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [busy, onCancel])

  const confirm = async () => {
    setBusy(true)
    try {
      await onConfirm()
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal" onMouseDown={(e) => { if (e.target === e.currentTarget && !busy) onCancel() }}>
      <div className="modal__card modal__card--small confirm" role="dialog" aria-label="Delete job">
        <h2>Delete this job?</h2>
        <p>
          <strong>{job.company}</strong> &middot; {job.role}
        </p>
        <p className="confirm__note">The job and its description will be removed. This cannot be undone.</p>
        <div className="confirm__bar">
          <button className="btn" onClick={onCancel} disabled={busy}>Cancel</button>
          <button className="btn btn--primary btn--red" onClick={confirm} disabled={busy} autoFocus>
            {busy ? 'Deleting...' : 'Delete'}
          </button>
        </div>
      </div>
    </div>
  )
}
