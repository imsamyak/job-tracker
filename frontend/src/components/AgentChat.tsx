import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { api } from '../api'
import { CloseIcon, SendIcon, SparklesIcon } from './Icons'
import './AgentChat.css'

interface Turn {
  role: 'user' | 'assistant'
  text: string
  done?: string[]
  confirm?: { label: string; jobId: number }[]
}

const IDEAS = ['What is pending?', 'Check my Gmail now', 'Show my notifications']

/** The "Ask AI" assistant: a chat in a panel on the right. It can answer, move jobs, add a job from a link, check Gmail and open pages. */
export default function AgentChat() {
  const [open, setOpen] = useState(false)
  const [turns, setTurns] = useState<Turn[]>([])
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)
  const end = useRef<HTMLDivElement>(null)

  useEffect(() => {
    end.current?.scrollIntoView({ block: 'end' })
  }, [turns, busy])

  useEffect(() => {
    if (!open) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false)
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open])

  const send = async (text: string) => {
    const t = text.trim()
    if (!t || busy) return
    const next: Turn[] = [...turns, { role: 'user', text: t }]
    setTurns(next)
    setDraft('')
    setBusy(true)
    try {
      const r = await api.askAi(next.map(({ role, text }) => ({ role, text })))
      setTurns([...next, { role: 'assistant', text: r.reply, done: r.done, confirm: r.confirm }])
      if (r.open) window.location.hash = r.open
    } catch (e) {
      setTurns([...next, { role: 'assistant', text: e instanceof Error ? e.message : 'Something went wrong.' }])
    } finally {
      setBusy(false)
    }
  }

  const confirmDelete = async (turn: number, jobId: number) => {
    try {
      await api.deleteJob(jobId)
      setTurns((all) => all.map((t, i) => (i === turn ? { ...t, confirm: t.confirm?.filter((c) => c.jobId !== jobId), done: [...(t.done ?? []), 'Deleted.'] } : t)))
    } catch (e) {
      setTurns((all) => all.map((t, i) => (i === turn ? { ...t, done: [...(t.done ?? []), e instanceof Error ? e.message : 'Could not delete.'] } : t)))
    }
  }

  return (
    <>
      <button className="agentbtn" title="Ask AI" aria-label="Ask AI" onClick={() => setOpen((o) => !o)}>
        <SparklesIcon /> <span>Ask AI</span>
      </button>
      {open && createPortal(
        <aside className="agent" role="dialog" aria-label="Ask AI">
          <header className="agent__head">
            <strong>Ask AI</strong>
            <button className="exticon" title="Close" aria-label="Close" onClick={() => setOpen(false)}><CloseIcon /></button>
          </header>
          <div className="agent__thread">
            {turns.length === 0 && (
              <div className="agent__empty">
                <p className="muted">Ask anything about your jobs, or tell me what to do: move a job, add one from a link, check Gmail.</p>
                <div className="askchat__chips">
                  {IDEAS.map((q) => <button key={q} className="askchat__chip" onClick={() => void send(q)}>{q}</button>)}
                </div>
              </div>
            )}
            {turns.map((t, i) => (
              <div key={i} className={t.role === 'user' ? 'msg msg--user' : 'msg msg--bot'}>
                <div className="msg__bubble">
                  {t.text}
                  {t.done && t.done.length > 0 && <ul className="agent__done">{t.done.map((d, k) => <li key={k}>{d}</li>)}</ul>}
                  {t.confirm?.map((c) => (
                    <div key={c.jobId} className="agent__confirm">
                      <span>{c.label}?</span>
                      <button className="btn" onClick={() => setTurns((all) => all.map((x, n) => (n === i ? { ...x, confirm: x.confirm?.filter((y) => y.jobId !== c.jobId) } : x)))}>No</button>
                      <button className="btn btn--primary btn--red" onClick={() => void confirmDelete(i, c.jobId)}>Delete</button>
                    </div>
                  ))}
                </div>
              </div>
            ))}
            {busy && <div className="msg msg--bot"><div className="msg__bubble"><span className="spinner" aria-hidden /> Thinking</div></div>}
            <div ref={end} />
          </div>
          <form className="askchat__composer" onSubmit={(e) => { e.preventDefault(); void send(draft) }}>
            <textarea
              rows={1}
              value={draft}
              placeholder="Ask or tell me what to do"
              aria-label="Your message"
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); void send(draft) } }}
            />
            <button className="askchat__send" disabled={busy || !draft.trim()} aria-label="Send"><SendIcon /></button>
          </form>
        </aside>,
        document.body,
      )}
    </>
  )
}
