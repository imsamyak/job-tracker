import { useState } from 'react'
import { api } from '../../api'
import { SparklesIcon } from '../../components/Icons'

/**
 * "Rewrite with AI" under a piece of text. Opens a small panel: say what you want (optional), see the proposed
 * version, then use it or throw it away. Nothing changes until you press Use this.
 */
export default function RewriteButton({ kind, text, context, onUse, hint }: {
  kind: 'ABOUT' | 'EXPERIENCE' | 'PROJECT' | 'SKILLS'
  text: string
  context: string
  onUse: (next: string) => void
  /** Example of what to ask, shown in the request box. */
  hint?: string
}) {
  const [open, setOpen] = useState(false)
  const [instruction, setInstruction] = useState('')
  const [busy, setBusy] = useState(false)
  const [proposal, setProposal] = useState('')
  const [error, setError] = useState('')

  const run = async () => {
    if (busy || !text.trim()) return
    setBusy(true)
    setError('')
    setProposal('')
    try {
      setProposal((await api.rewrite(kind, text, context, instruction)).text)
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not rewrite that.')
    } finally {
      setBusy(false)
    }
  }

  const close = () => {
    setOpen(false)
    setProposal('')
    setError('')
  }

  if (!open) {
    return (
      <button type="button" className="btn rewrite__open" disabled={!text.trim()} title="Rewrite this with AI" onClick={() => setOpen(true)}>
        <SparklesIcon /> Rewrite
      </button>
    )
  }

  return (
    <div className="rewrite">
      <div className="rewrite__ask">
        <input
          value={instruction}
          placeholder={hint ?? 'Optional: tell AI what you want, for example make it shorter'}
          aria-label="What should change"
          disabled={busy}
          autoFocus
          onChange={(e) => setInstruction(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); void run() } }}
        />
        <button type="button" className="btn btn--primary" disabled={busy} onClick={() => void run()}>
          {busy ? <><span className="spinner" aria-hidden /> Writing</> : proposal ? 'Try again' : 'Rewrite'}
        </button>
        <button type="button" className="btn" onClick={close}>Close</button>
      </div>
      {error && <p className="conn__error">{error}</p>}
      {proposal && (
        <div className="rewrite__result">
          <pre>{proposal}</pre>
          <div className="rewrite__bar">
            <button type="button" className="btn" onClick={() => setProposal('')}>Discard</button>
            <button type="button" className="btn btn--primary" onClick={() => { onUse(proposal); close() }}>Use this</button>
          </div>
        </div>
      )}
    </div>
  )
}
