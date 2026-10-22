import { useEffect, useRef, useState } from 'react'
import { CheckIcon, CopyIcon, SendIcon } from '../Icons'
import { useCopy } from './useCopy'

export interface Message {
  role: 'user' | 'assistant'
  text: string
}

const SUGGESTIONS = ['Why do you want to work here?', 'Tell me about yourself', 'Why should we hire you?']

/**
 * "Ask about this application" as a conversation: you ask, it replies, you follow up, it replies. Replies are
 * written from your background and the job, and each has a copy button for pasting into the form.
 * Design only for now (answering needs Claude and your profile), so sending is switched off.
 */
export default function AskCard({ initial = [] }: { initial?: Message[] }) {
  const [messages] = useState<Message[]>(initial)
  const [draft, setDraft] = useState('')
  const { copied, copy } = useCopy()
  const thread = useRef<HTMLDivElement>(null)

  // the newest message is always in view
  useEffect(() => {
    thread.current?.scrollTo({ top: thread.current.scrollHeight })
  }, [messages])

  return (
    <section className="card acard askchat">
      <header className="acard__head">
        <h2>Ask</h2>
        <span className="soon">Soon</span>
      </header>

      <div className="askchat__thread" ref={thread} aria-live="polite">
        {messages.length === 0 ? (
          <div className="askchat__empty">
            <p className="muted">Ask anything from the application form. Answers come from your background and this job, and you can follow up.</p>
            <div className="askchat__chips">
              {SUGGESTIONS.map((q) => (
                <button key={q} className="askchat__chip" onClick={() => setDraft(q)}>{q}</button>
              ))}
            </div>
          </div>
        ) : (
          messages.map((m, i) => (
            <div key={i} className={m.role === 'user' ? 'msg msg--user' : 'msg msg--bot'}>
              <div className="msg__bubble">{m.text}</div>
              {m.role === 'assistant' && (
                <button
                  className="msg__copy"
                  title="Copy"
                  aria-label="Copy this answer"
                  onClick={() => copy(String(i), m.text)}
                >
                  {copied === String(i) ? <CheckIcon /> : <CopyIcon />}
                </button>
              )}
            </div>
          ))
        )}
      </div>

      <form className="askchat__composer" onSubmit={(e) => e.preventDefault()}>
        <textarea
          rows={1}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          placeholder="Ask a question or follow up"
          aria-label="Your message"
        />
        <button className="askchat__send" disabled title="Coming soon" aria-label="Send">
          <SendIcon />
        </button>
      </form>
    </section>
  )
}
